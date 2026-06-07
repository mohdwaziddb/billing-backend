package com.billing.repository;

import com.billing.entity.Company;
import com.billing.entity.User;
import com.billing.entity.enums.RoleName;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByEmailIgnoreCase(String email);
    Optional<User> findByMobileNumber(String mobileNumber);
    boolean existsByEmailIgnoreCase(String email);
    boolean existsByMobileNumber(String mobileNumber);
    List<User> findByCompanyOrderByCreatedAtDesc(Company company);
    Page<User> findByCompanyOrderByCreatedAtDesc(Company company, Pageable pageable);
    Optional<User> findByIdAndCompany(Long id, Company company);

    @Query("""
            select u from User u
            where u.company = :company
              and (:name is null or lower(u.fullName) like lower(concat('%', :name, '%')))
              and (:mobileNumber is null or u.mobileNumber like concat('%', :mobileNumber, '%'))
              and (:email is null or lower(u.email) like lower(concat('%', :email, '%')))
              and (:search is null
                or lower(u.fullName) like lower(concat('%', :search, '%'))
                or u.mobileNumber like concat('%', :search, '%')
                or lower(u.email) like lower(concat('%', :search, '%')))
              and (:role is null or u.role = :role)
              and (:active is null or u.active = :active)
            order by u.createdAt desc
            """)
    Page<User> searchCompanyUsers(@Param("company") Company company,
                                  @Param("name") String name,
                                  @Param("mobileNumber") String mobileNumber,
                                  @Param("email") String email,
                                  @Param("search") String search,
                                  @Param("role") RoleName role,
                                  @Param("active") Boolean active,
                                  Pageable pageable);
}
