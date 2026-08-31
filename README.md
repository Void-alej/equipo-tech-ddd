# EquipoTech DDD

Sistema de alquiler de equipos tecnológicos desarrollado en Java 17 y Maven.

## Descripción

EquipoTech permite gestionar clientes, equipos tecnológicos y alquileres.

El proyecto aplica conceptos de Domain-Driven Design (DDD) y separación de
responsabilidades inspirada en Arquitectura Hexagonal.

## Reglas del dominio

- Un equipo solo puede reservarse cuando está disponible.
- Un equipo reservado puede pasar a estado alquilado.
- Un equipo alquilado puede ser devuelto.
- No se permiten fechas de alquiler inválidas.
- El precio total se calcula según el número de días.
- Los datos como correo, teléfono y dinero se manejan mediante Value Objects.
- Las operaciones inválidas generan excepciones de dominio.

## Tecnologías

- Java 17
- Maven
- JUnit 5
- Domain-Driven Design
- Arquitectura Hexagonal

## Ejecución

Para compilar:

```bash
mvn clean compile