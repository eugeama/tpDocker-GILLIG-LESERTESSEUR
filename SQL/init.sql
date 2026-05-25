USE my_database;

CREATE TABLE IF NOT EXISTS usuarios (
    id      INT          NOT NULL AUTO_INCREMENT,
    nombre  VARCHAR(255) NOT NULL,
    creado  DATE         NOT NULL,
    PRIMARY KEY (id)
);

INSERT INTO usuarios (nombre, creado) VALUES ('Admin', CURDATE());