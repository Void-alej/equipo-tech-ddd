# NexusMarket

Marketplace desarrollado en Java 17 y Maven. La aplicación permite gestionar compradores, vendedores, catálogo de productos físicos y digitales, inventario, carritos, pedidos, pagos de demostración, envíos, devoluciones y reportes.

## Requisitos

- JDK 17.
- Maven 3.9 o superior.

## Ejecutar la aplicación

Desde la raíz del repositorio:

```bash
mvn clean test
mvn exec:java -Dexec.mainClass=com.nexusmarket.infrastructure.http.NexusMarketServer
```

Abre [http://localhost:8080](http://localhost:8080).

El servidor utiliza el puerto `8080` por defecto. Puedes cambiarlo mediante la variable de entorno `PORT`.

### Cuenta administrativa inicial

En una instalación nueva se crea esta cuenta:

- Correo: `admin@nexusmarket.local`
- Contraseña inicial: `Admin123!`

También se crea una cuenta de operador logístico:

- Correo: `logistica@nexusmarket.local`
- Contraseña inicial: `Logistica123!`

Antes de una demostración compartida, configura contraseñas propias mediante las variables `NEXUSMARKET_ADMIN_PASSWORD` y `NEXUSMARKET_LOGISTICS_PASSWORD`. Esas variables se aplican al crear por primera vez las credenciales; no reemplazan contraseñas ya almacenadas.

## Persistencia

Los repositorios utilizan almacenamiento local serializado para conservar usuarios, perfiles, productos, bodegas, inventario, movimientos, carritos, pedidos, reservas, asignaciones, envíos, devoluciones, facturas y credenciales entre reinicios.

Por defecto, los archivos se guardan en `data/nexusmarket/`. Esta ruta se excluye de Git. Puedes cambiarla con `NEXUSMARKET_DATA_DIR`.

Las sesiones activas son temporales: al reiniciar el servidor se debe iniciar sesión nuevamente. Las credenciales se almacenan como hashes PBKDF2-HMAC-SHA256 con sal aleatoria; no se guarda la contraseña en texto plano.

## Interfaz y API

La interfaz web llama a la API REST del mismo servidor. Después de las operaciones principales vuelve a consultar los recursos mediante GET para mostrar el estado actualizado.

Endpoints principales:

- `POST /api/auth/login` y `POST /api/auth/logout`.
- `GET /api/me`, `GET /api/users`.
- `GET /api/products`, `POST /api/products`, `POST /api/products/{id}/publish`.
- `GET /api/warehouses`, `GET /api/inventory`, `POST /api/inventory`.
- `POST /api/buyers`, `POST /api/sellers`, `POST /api/logistics-operators`.
- `POST /api/carts`, `POST /api/carts/{id}/items`, `GET /api/carts/{id}`.
- `POST /api/orders`, `GET /api/orders`, `POST /api/orders/{id}/pay`.
- `POST /api/orders/{id}/shipments`, `POST /api/shipments/{id}/{transition}`.
- `POST /api/orders/{id}/returns`, `POST /api/returns/{id}/{transition}`.
- `GET /api/reports/seller`, `GET /api/reports/admin`.

Las operaciones protegidas requieren el encabezado `Authorization: Bearer <token>` que entrega el inicio de sesión. El servidor resuelve el usuario registrado para cada solicitud y aplica controles por rol.

## Flujos de demostración

1. Inicia sesión como administrador y crea tres compradores con perfil y credenciales.
2. Incorpora un vendedor y su primera bodega.
3. Inicia sesión como vendedor, crea y publica productos físicos y digitales.
4. Inicia sesión como operador logístico e ingresa existencias físicas en la bodega Marketplace.
5. Inicia sesión con cada comprador, crea un carrito, agrega productos y confirma el pedido. La API rechaza cantidades físicas superiores a las existencias reservables.
6. Aprueba el pago como comprador, crea los envíos como operador logístico y ejecuta las transiciones de despacho hasta la entrega.
7. Solicita la devolución de un artículo, apruébala como administrador, registra la recepción física como operador logístico y confirma el reembolso como administrador.
8. Consulta el reporte del vendedor y el administrativo.
9. Reinicia el servidor, inicia sesión nuevamente y consulta los mismos GET para verificar la persistencia.

## Alcances de la demostración

- El pago se simula con un resultado aprobado o rechazado. No hay conexión a una pasarela real.
- El reembolso se registra mediante una referencia externa. No se transfiere dinero desde la aplicación.
- La persistencia es local basada en archivos serializados; no es una base de datos relacional ni debe exponerse directamente a Internet sin endurecimiento adicional.
- Los datos persistentes deben conservarse entre reinicios del proceso mientras se mantenga la carpeta configurada.

## Pruebas

```bash
mvn clean test
```

El workflow de GitHub Actions ejecuta la misma suite en cada push a esta rama y en los pull requests dirigidos a `main`.
