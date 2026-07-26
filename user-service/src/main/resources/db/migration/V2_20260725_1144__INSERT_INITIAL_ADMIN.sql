INSERT INTO users (fullname, email, username, password, role, active, created_at, updated_at)
VALUES ('Platform Admin',
        'admin@ecommerce.com',
        'admin',
        '$2a$10$tcn18t9LoiCcCmKOzb38keR8BbpMwdodrjZ/W3lsgp8wD/OwwFUn2', -- Admin#23
        'SUPER_ADMIN',
        TRUE,
        now(),
        now());