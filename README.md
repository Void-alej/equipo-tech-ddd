
# NexusMarket

Sistema de gestión de un marketplace que centraliza las operaciones comerciales entre compradores, vendedores y operadores logísticos. Desarrollado en Java 17 con Maven, aplicando Domain-Driven Design (DDD) y principios de Arquitectura Hexagonal.

## Descripción

NexusMarket permite gestionar el ciclo comercial de un marketplace, desde la administración de usuarios y el catálogo de productos hasta las compras, los pedidos, la logística, la facturación y los procesos de posventa.

El sistema contempla productos físicos y digitales, inventario distribuido en bodegas y diferentes roles con responsabilidades específicas.

## Módulos funcionales

El proyecto contempla los siguientes módulos:

- **Administración de usuarios:** gestión de usuarios, roles y estados.
- **Gestión de vendedores:** incorporación y administración de vendedores.
- **Gestión de compradores:** administración de información comercial y direcciones.
- **Gestión de bodegas:** administración de espacios de almacenamiento.
- **Catálogo de productos:** registro, publicación, suspensión y descontinuación de productos, incluyendo variantes.
- **Inventario distribuido:** control de existencias y movimientos asociados a productos y bodegas.
- **Carrito de compras:** selección y administración de productos antes de confirmar una compra.
- **Gestión de pedidos:** administración del ciclo de vida de los pedidos y sus estados.
- **Facturación:** gestión de la información comercial de las compras.
- **Logística:** administración de envíos y procesos de despacho.
- **Devoluciones y reembolsos:** gestión de procesos de posventa.
- **Reportes administrativos:** consolidación y consulta de información operativa.

## Roles del sistema

NexusMarket contempla cinco roles:

- **Comprador:** consulta productos y realiza compras.
- **Vendedor:** registra y administra sus productos.
- **Operador logístico:** participa en la administración del inventario y los despachos.
- **Administrador:** incorpora vendedores y administra bodegas.
- **Supervisor:** consulta y realiza seguimiento operativo.

Cada usuario tiene un único rol y sus operaciones deben respetar las responsabilidades asignadas.

## Reglas de negocio principales

- El correo electrónico y el documento de identidad deben ser únicos.
- Cada usuario tiene un único rol.
- Los vendedores no pueden registrarse por sí mismos; deben ser incorporados por un administrador.
- El inventario debe estar asociado a un producto y una bodega.
- No se permiten existencias negativas.
- No se puede reservar inventario inexistente o marcado como dañado.
- Los productos físicos requieren inventario y despacho.
- Los productos digitales se entregan automáticamente después de aprobarse el pago.
- Un pedido finalizado no puede modificarse.

## Tecnologías

- Java 17
- Maven
- JUnit 5
- Domain-Driven Design (DDD)
- Arquitectura Hexagonal

## Estructura del proyecto

El código fuente está organizado en paquetes Java:

- `src/main/java/com/nexusmarket`: código principal de NexusMarket.
- `src/test/java/com/nexusmarket`: pruebas unitarias y de integración de NexusMarket.

## Ejecución

Para compilar el proyecto:

```bash
mvn clean compile
```

Para ejecutar las pruebas:

```bash
mvn clean test
```

## Alcance

NexusMarket implementa los procesos funcionales definidos en la especificación del negocio.

Las interfaces gráficas, aplicaciones móviles, portales web, mecanismos de autenticación técnica y detalles específicos de infraestructura o almacenamiento están fuera del alcance de dicha especificación.
