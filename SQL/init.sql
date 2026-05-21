USE my_database;

CREATE TABLE IF NOT EXISTS usuarios (
    id      INT          NOT NULL,
    nombre  VARCHAR(255) NOT NULL,
    creado  DATE         NOT NULL,
    PRIMARY KEY (id)
);

INSERT INTO usuarios (id, nombre, creado) VALUES (1, 'Admin', CURDATE());