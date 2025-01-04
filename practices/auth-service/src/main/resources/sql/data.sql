INSERT INTO USERS (username, password, age, created_at, updated_at)
VALUES ('admin', '{bcrypt}$2a$10$zaIqU9QHGOGL.Jv2tfO/8eSmHpdEa469tRoPcGYOCEGIzEtvPbMY2', 0, NOW(), NOW());

INSERT INTO ROLE (name, description, is_expression, created_at, updated_at)
VALUES ('ROLE_ADMIN', '관리자', false, NOW(), NOW()),
       ('ROLE_USER', '회원', false, NOW(), NOW());

INSERT INTO USER_ROLE (user_id, role_id, created_at, updated_at)
VALUES (
            (SELECT id FROM USERS WHERE username = 'admin'),
            (SELECT id FROM ROLE WHERE name = 'ROLE_ADMIN'),
            NOW(),
            NOW()
       );