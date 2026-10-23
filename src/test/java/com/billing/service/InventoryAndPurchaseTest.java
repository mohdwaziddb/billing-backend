package com.billing.service;

import com.billing.dto.purchase.PurchaseItemRequest;
import com.billing.dto.purchase.PurchaseRequest;
import com.billing.entity.Company;
import com.billing.entity.InventoryLedgerEntry;
import com.billing.entity.Invoice;
import com.billing.entity.InvoiceItem;
import com.billing.entity.InvoiceItemAllocation;
import com.billing.entity.Product;
import com.billing.entity.ProductBatch;
import com.billing.entity.Purchase;
import com.billing.entity.enums.InventoryConsumptionMethod;
import com.billing.entity.enums.InventoryLedgerMovementType;
import com.billing.entity.enums.ProductBatchStatus;
import com.billing.exception.BadRequestException;
import com.billing.repository.InventoryLedgerRepository;
import com.billing.repository.InvoiceItemAllocationRepository;
import com.billing.repository.ProductBatchRepository;
import com.billing.repository.PurchaseRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InventoryAndPurchaseTest {

    @Mock
    private AccessControlService accessControlService;
    @Mock
    private ProductBatchRepository productBatchRepository;
    @Mock
    private InventoryLedgerRepository inventoryLedgerRepository;
    @Mock
    private InvoiceItemAllocationRepository invoiceItemAllocationRepository;
    @Mock
    private AuditNameResolver auditNameResolver;
    @Mock
    private PurchaseRepository purchaseRepository;
    @Mock
    private InventoryService inventoryServiceMock;
    @Mock
    private AuditLogService auditLogService;

    private final Product product = Product.builder().id(1L).name("Pen").sku("PEN").build();

    private InventoryService inventoryService() {
        return new InventoryService(accessControlService, productBatchRepository, inventoryLedgerRepository,
                invoiceItemAllocationRepository, auditNameResolver);
    }

    // ---------------- Inventory: sale allocation ----------------

    @Test
    void fifoTakesOldestBatchFirstAndExhaustsIt() {
        Company company = Company.builder().id(1L).inventoryConsumptionMethod(InventoryConsumptionMethod.FIFO).build();
        ProductBatch older = batch(11L, 10, "5.00");
        ProductBatch newer = batch(12L, 10, "6.00");
        when(productBatchRepository.lockByCompanyAndProductOrderByBatchDateAscIdAsc(company, product)).thenReturn(List.of(older, newer));
        when(invoiceItemAllocationRepository.save(any(InvoiceItemAllocation.class))).thenAnswer(call -> call.getArgument(0));

        inventoryService().allocateForInvoice(invoice(company, 15));

        assertEquals(0, older.getRemainingQty());
        assertEquals(ProductBatchStatus.EXHAUSTED, older.getBatchStatus());
        assertEquals(5, newer.getRemainingQty());
        assertEquals(ProductBatchStatus.ACTIVE, newer.getBatchStatus());
        ArgumentCaptor<InvoiceItemAllocation> allocations = ArgumentCaptor.forClass(InvoiceItemAllocation.class);
        verify(invoiceItemAllocationRepository, org.mockito.Mockito.times(2)).save(allocations.capture());
        // COGS: 10 x 5 + 5 x 6
        assertEquals(new BigDecimal("50.00"), allocations.getAllValues().get(0).getCostAmount());
        assertEquals(new BigDecimal("30.00"), allocations.getAllValues().get(1).getCostAmount());
    }

    @Test
    void lifoUsesNewestBatchQuery() {
        Company company = Company.builder().id(1L).inventoryConsumptionMethod(InventoryConsumptionMethod.LIFO).build();
        ProductBatch newest = batch(12L, 10, "6.00");
        when(productBatchRepository.lockByCompanyAndProductOrderByBatchDateDescIdDesc(company, product)).thenReturn(List.of(newest));
        when(invoiceItemAllocationRepository.save(any(InvoiceItemAllocation.class))).thenAnswer(call -> call.getArgument(0));

        inventoryService().allocateForInvoice(invoice(company, 4));

        assertEquals(6, newest.getRemainingQty());
        verify(productBatchRepository, never()).lockByCompanyAndProductOrderByBatchDateAscIdAsc(any(), any());
    }

    @Test
    void insufficientStockIsRejected() {
        Company company = Company.builder().id(1L).inventoryConsumptionMethod(InventoryConsumptionMethod.FIFO).build();
        when(productBatchRepository.lockByCompanyAndProductOrderByBatchDateAscIdAsc(company, product)).thenReturn(List.of(batch(11L, 3, "5.00")));
        when(invoiceItemAllocationRepository.save(any(InvoiceItemAllocation.class))).thenAnswer(call -> call.getArgument(0));

        assertThrows(BadRequestException.class, () -> inventoryService().allocateForInvoice(invoice(company, 5)));
    }

    @Test
    void releasingAllocationPutsStockBack() {
        Company company = Company.builder().id(1L).build();
        ProductBatch batch = batch(11L, 2, "5.00");
        batch.setBatchStatus(ProductBatchStatus.EXHAUSTED);
        Invoice invoice = invoice(company, 8);
        InvoiceItemAllocation allocation = InvoiceItemAllocation.builder().id(99L).product(product).productBatch(batch)
                .invoice(invoice).invoiceItem(invoice.getItems().get(0)).allocatedQty(8)
                .purchaseRate(new BigDecimal("5.00")).sellingRate(new BigDecimal("9.00")).active(true).build();
        when(invoiceItemAllocationRepository.findByCompanyAndInvoiceAndActiveTrueOrderByIdAsc(company, invoice)).thenReturn(List.of(allocation));
        when(productBatchRepository.lockById(11L)).thenReturn(Optional.of(batch));

        inventoryService().releaseInvoiceAllocations(invoice, "test");

        assertEquals(10, batch.getRemainingQty());
        assertEquals(ProductBatchStatus.ACTIVE, batch.getBatchStatus());
        assertFalse(allocation.isActive());
        ArgumentCaptor<InventoryLedgerEntry> ledger = ArgumentCaptor.forClass(InventoryLedgerEntry.class);
        verify(inventoryLedgerRepository).save(ledger.capture());
        assertEquals(InventoryLedgerMovementType.SALE_REVERSAL, ledger.getValue().getMovementType());
        assertEquals(8, ledger.getValue().getQtyIn());
    }

    // ---------------- Purchase ----------------

    private PurchaseService purchaseService() {
        return new PurchaseService(purchaseRepository, null, productBatchRepository, invoiceItemAllocationRepository,
                accessControlService, null, inventoryServiceMock, auditLogService, auditNameResolver, null);
    }

    @Test
    void deletingPurchaseKeepsHistoryAndZeroesStock() {
        Company company = Company.builder().id(1L).build();
        Purchase purchase = Purchase.builder().id(5L).purchaseNo("PUR/2627/00001").purchaseDate(LocalDate.of(2026, 10, 1))
                .items(new ArrayList<>()).active(true).build();
        ProductBatch batch = batch(21L, 7, "5.00");
        when(accessControlService.getCurrentCompany("u")).thenReturn(company);
        when(purchaseRepository.findByIdWithItemsAndCompany(5L, company)).thenReturn(Optional.of(purchase));
        when(productBatchRepository.findByCompanyAndPurchaseOrderByBatchDateAscIdAsc(company, purchase)).thenReturn(List.of(batch));
        when(invoiceItemAllocationRepository.countByCompanyAndProductBatchInAndActiveTrue(eq(company), anyList())).thenReturn(0L);
        when(productBatchRepository.lockById(21L)).thenReturn(Optional.of(batch));

        purchaseService().delete("u", 5L);

        // Batch/ledger delete nahi hote (pehle hard delete = FK error + history gayab).
        verify(productBatchRepository, never()).deleteAll(any());
        verify(inventoryLedgerRepository, never()).deleteAll(any());
        assertEquals(0, batch.getRemainingQty());
        assertEquals(ProductBatchStatus.EXHAUSTED, batch.getBatchStatus());
        ArgumentCaptor<InventoryLedgerEntry> ledger = ArgumentCaptor.forClass(InventoryLedgerEntry.class);
        verify(inventoryServiceMock).createLedgerEntry(ledger.capture());
        assertEquals(InventoryLedgerMovementType.PURCHASE_RETURN, ledger.getValue().getMovementType());
        assertEquals(7, ledger.getValue().getQtyOut());
        assertFalse(purchase.isActive());
    }

    @Test
    void purchaseWithSoldStockCannotBeDeleted() {
        Company company = Company.builder().id(1L).build();
        Purchase purchase = Purchase.builder().id(5L).items(new ArrayList<>()).active(true).build();
        when(accessControlService.getCurrentCompany("u")).thenReturn(company);
        when(purchaseRepository.findByIdWithItemsAndCompany(5L, company)).thenReturn(Optional.of(purchase));
        when(productBatchRepository.findByCompanyAndPurchaseOrderByBatchDateAscIdAsc(company, purchase)).thenReturn(List.of(batch(21L, 2, "5.00")));
        when(invoiceItemAllocationRepository.countByCompanyAndProductBatchInAndActiveTrue(eq(company), anyList())).thenReturn(3L);

        assertThrows(BadRequestException.class, () -> purchaseService().delete("u", 5L));
    }

    @Test
    void invalidPurchaseRequestsAreRejected() {
        assertThrows(BadRequestException.class, () -> purchaseService().create("u", purchaseRequest(null, 1, "5.00")));
        assertThrows(BadRequestException.class, () -> purchaseService().create("u", purchaseRequest(LocalDate.now(), -2, "5.00")));
        assertThrows(BadRequestException.class, () -> purchaseService().create("u", purchaseRequest(LocalDate.now(), 1, "-5.00")));
    }

    // ---------------- helpers ----------------

    private ProductBatch batch(Long id, int remaining, String rate) {
        return ProductBatch.builder().id(id).product(product).remainingQty(remaining).purchaseQty(remaining)
                .purchaseRate(new BigDecimal(rate)).sellingRate(new BigDecimal("9.00"))
                .batchStatus(ProductBatchStatus.ACTIVE).build();
    }

    private Invoice invoice(Company company, int qty) {
        Invoice invoice = Invoice.builder().id(1L).company(company).invoiceNo("INV/2627/00001")
                .invoiceDate(LocalDate.of(2026, 10, 22)).items(new ArrayList<>()).build();
        invoice.getItems().add(InvoiceItem.builder().id(2L).invoice(invoice).product(product).qty(qty).price(new BigDecimal("9.00")).build());
        return invoice;
    }

    private PurchaseRequest purchaseRequest(LocalDate date, int qty, String rate) {
        PurchaseItemRequest item = new PurchaseItemRequest();
        item.setProductId(1L);
        item.setQty(qty);
        item.setPurchaseRate(new BigDecimal(rate));
        item.setSellingRate(new BigDecimal("9.00"));
        PurchaseRequest request = new PurchaseRequest();
        request.setPurchaseDate(date);
        request.setItems(List.of(item));
        return request;
    }
}
