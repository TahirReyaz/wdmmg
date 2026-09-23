-- Predefined expense types. Admins can add, edit, retire or delete them from the Admin page.
INSERT INTO categories (name, icon, color, sort_order) VALUES
    ('Food & Dining',          'utensils',        '#e34948', 1),
    ('Groceries',              'shopping-basket', '#008300', 2),
    ('Transport',              'car',             '#2a78d6', 3),
    ('Rent & Housing',         'home',            '#4a3aa7', 4),
    ('Utilities',              'zap',             '#eda100', 5),
    ('Bills & Subscriptions',  'receipt',         '#9d755d', 6),
    ('Shopping',               'shopping-bag',    '#e87ba4', 7),
    ('Entertainment',          'film',            '#eb6834', 8),
    ('Health & Fitness',       'heart-pulse',     '#1baf7a', 9),
    ('Travel',                 'plane',           '#0e7c86', 10),
    ('Education',              'graduation-cap',  '#7a5195', 11),
    ('Personal Care',          'sparkles',        '#d55181', 12),
    ('Gifts & Donations',      'gift',            '#8f6d31', 13),
    ('Other',                  'circle',          '#64748b', 99);
