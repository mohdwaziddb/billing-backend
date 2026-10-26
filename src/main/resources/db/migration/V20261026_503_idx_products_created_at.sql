-- Products page + product dropdowns (size=1000): "ORDER BY created_at DESC".
CREATE INDEX idx_products_created_at ON products (created_at);
