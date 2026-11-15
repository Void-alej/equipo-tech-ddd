# Guía de sustentación integral — NexusMarket

> Documento operativo para preparar y grabar la demostración. Está basado en los endpoints y reglas presentes en el código de `main`. No reemplaza la verificación real en la interfaz y en los GET durante el video.

## 1. Alcance y advertencias

- Ejecutar desde la raíz del repositorio con Java 17 y Maven.
- La aplicación conserva datos en `data/nexusmarket/`. **No borrar esa carpeta** para preparar la demo: contiene pedidos y operaciones existentes. Si se necesita una instalación limpia, primero hacer una copia de seguridad fuera del repositorio.
- El pago es simulado: `approved: true` registra un pago aprobado; no existe pasarela bancaria real.
- El reembolso registra una referencia externa; no transfiere dinero.
- Los productos pertenecen a un vendedor, no a un comprador. Por tanto, la frase del comunicado «a cada comprador se le deben crear dos productos» no se corresponde literalmente con el modelo de dominio. La interpretación técnica viable es crear tres compradores y un catálogo de al menos seis productos (físicos y digitales) de un vendedor, y vincular cuatro de ellos a la compra demostrativa. Si el docente exige una relación comprador-producto distinta, debe aclararse antes de grabar.

## 2. Arranque y comprobación

Terminal 1:

```bash
mvn clean test
mvn exec:java -Dexec.mainClass=com.nexusmarket.infrastructure.http.NexusMarketServer
```

En otra terminal:

```bash
curl -i http://localhost:8080/api/health
```

Debe responder HTTP 200 y `{"status":"UP","application":"NexusMarket"}`. Abrir `http://localhost:8080` en el navegador.

## 3. Roles y credenciales

Instalación nueva:

| Rol | Cuenta inicial |
|---|---|
| Administrador | `admin@nexusmarket.local` / `Admin123!` |
| Operador logístico | `logistica@nexusmarket.local` / `Logistica123!` |

Los compradores y vendedor se crean durante la preparación de la demo. Usar correos únicos, contraseñas de al menos 8 caracteres y guardar las credenciales de forma privada. No publicar contraseñas en el video.

## 4. Orden de preparación del escenario

### 4.1 Administrador: crear tres compradores y vendedor

En la interfaz, iniciar sesión como administrador y crear tres compradores con nombre, correo, documento, dirección principal y contraseña. Después de **cada creación**, verificar:

- `GET /api/users`
- `GET /api/buyers/{buyerId}/profile` para confirmar el perfil de ese comprador.

Crear también un vendedor y su primera bodega desde el formulario de incorporación. Verificar inmediatamente:

- `GET /api/users`
- `GET /api/warehouses`

La API permite crear compradores y vendedores solo al administrador.

### 4.2 Vendedor: crear y publicar productos

Cerrar sesión e iniciar sesión con el vendedor. Crear al menos seis productos para disponer de dos productos por comprador en el escenario de preparación: combina tipos `PHYSICAL` y `DIGITAL`. El modelo no vincula productos a compradores; todos quedan asociados al vendedor autenticado.

Después de cada producto:

- `GET /api/products/mine`
- `GET /api/products` para comprobar el catálogo publicado cuando corresponda.

Publicar cada producto mediante `POST /api/products/{productId}/publish` y consultar de nuevo `GET /api/products/mine` y `GET /api/products`.

### 4.3 Inventario físico: vendedor o logística

Los productos digitales no requieren envío físico. Para cada producto físico, registrar unidades en una bodega activa de tipo Marketplace. La interfaz permite ingresar existencias. Verificar cada operación con:

- `GET /api/inventory`
- `GET /api/warehouses`

Anotar el stock disponible de cada producto físico. Para la prueba de exceso, elegir uno con stock conocido y solicitar una cantidad mayor que ese stock.

### 4.4 Compradores: carritos y bloqueo por exceso

Iniciar sesión como cada comprador. Crear un carrito y verificar el identificador devuelto con `GET /api/carts/{cartId}`.

En el carrito de demostración agregar cuatro productos distintos: dos físicos y dos digitales. Tras cada adición, consultar `GET /api/carts/{cartId}`.

Antes de finalizar el carrito, intentar agregar del producto físico una cantidad superior a la disponible. La API debe rechazarla con HTTP 409 y mensaje de inventario insuficiente. Consultar otra vez el carrito y el inventario para mostrar que el intento no agregó unidades ni alteró el stock.

Crear el pedido desde el carrito mediante `POST /api/orders` y consultar inmediatamente:

- `GET /api/orders`
- `GET /api/orders/{orderId}`
- `GET /api/inventory`

La consulta de pedidos se filtra por permisos y por comprador.

### 4.5 Pago

Con el comprador dueño del pedido, aprobar el pago mediante `POST /api/orders/{orderId}/pay` con `{"approved":true}`. Consultar inmediatamente:

- `GET /api/orders/{orderId}`
- `GET /api/orders`
- `GET /api/inventory`

No repetir el pago sobre el mismo pedido. El pago es una simulación.

### 4.6 Envío: operador logístico

Iniciar sesión como operador logístico. Consultar primero `GET /api/orders` y seleccionar el pedido pagado. Crear los envíos mediante `POST /api/orders/{orderId}/shipments`.

Consultar `GET /api/shipments` y `GET /api/orders/{orderId}/shipments`. Por cada envío, ejecutar las transiciones en orden, consultando el GET correspondiente después de cada una:

1. `POST /api/shipments/{shipmentId}/pack`
2. `POST /api/shipments/{shipmentId}/dispatch`
3. `POST /api/shipments/{shipmentId}/transit`
4. `POST /api/shipments/{shipmentId}/deliver`

Después de cada transición: `GET /api/shipments`, `GET /api/orders/{orderId}/shipments` y `GET /api/orders/{orderId}`. Los pedidos exclusivamente digitales no generan envíos físicos.

### 4.7 Devolución parcial y reembolso

Con el comprador propietario del pedido ya entregado, solicitar la devolución de **una unidad de un producto físico** mediante `POST /api/orders/{orderId}/returns`, indicando `productId`, `quantity: 1` y un motivo. Consultar inmediatamente `GET /api/returns` y `GET /api/returns/{returnId}`.

Como administrador, consultar la devolución y aprobarla con `POST /api/returns/{returnId}/approve`. Verificar con `GET /api/returns/{returnId}`.

Como operador logístico (o administrador), registrar la recepción con `POST /api/returns/{returnId}/receive`, enviando el identificador de una bodega activa y la condición `SELLABLE` o `DAMAGED`. Para demostrar reingreso al inventario, usar `SELLABLE`. Consultar inmediatamente:

- `GET /api/returns/{returnId}`
- `GET /api/inventory`

Como administrador, confirmar el reembolso externo con `POST /api/returns/{returnId}/refund` y una referencia de reembolso no sensible. Consultar de nuevo `GET /api/returns/{returnId}` y `GET /api/orders/{orderId}`.

Volver a consultar el pedido y comparar los cuatro renglones: solo el producto físico devuelto debe tener una devolución registrada; los otros tres productos deben conservar cantidades y estado. No solicitar devolución del pedido completo.

### 4.8 Reporte del vendedor

Iniciar sesión como vendedor y consultar `GET /api/reports/seller`. Este reporte reúne productos del vendedor, inventario, inventario disponible, movimientos, pedidos, devoluciones y ventas por producto, incluyendo unidades vendidas, unidades en devolución, unidades reembolsadas, ventas brutas, valor reembolsado y ventas netas.

Guardar evidencia del reporte antes del reinicio (captura o grabación). El reporte de vendedor solo está autorizado para el rol `SELLER`.

## 5. Pruebas de autorización para mostrar en video

La API requiere `Authorization: Bearer <token>` para las rutas protegidas. Para demostrar controles, hacer al menos estas pruebas negativas y registrar el código HTTP:

| Sesión | Intento no autorizado | Resultado esperado |
|---|---|---|
| Comprador | `GET /api/inventory` | 403 |
| Comprador | `POST /api/products` | 403 |
| Comprador A | Consultar pedido/devolución/envío de comprador B | 403 |
| Vendedor | `POST /api/buyers` | 403 |
| Vendedor | `GET /api/reports/admin` | 403 |
| Operador logístico | `POST /api/products` | 403 |
| Operador logístico | `GET /api/reports/seller` | 403 |
| Administrador | `GET /api/reports/seller` | 403 |

Para cada prueba negativa, mostrar primero `GET /api/me` y después la operación bloqueada. No cambiar de usuario modificando únicamente el nombre visible: cerrar sesión e iniciar una sesión nueva con las credenciales del otro rol. Antes de cambiar de rol, consultar los GET relevantes del rol actual y dejar evidencia.

## 6. Reinicio y persistencia

1. Antes de detener el servidor, guardar evidencia de `GET /api/reports/seller`, `GET /api/inventory`, `GET /api/orders` y `GET /api/returns`.
2. Detener el servidor con `Ctrl+C`.
3. Iniciarlo de nuevo con el comando de la sección 2.
4. Iniciar sesión otra vez como vendedor (las sesiones son temporales y no sobreviven al reinicio).
5. Consultar de nuevo `GET /api/reports/seller`, `GET /api/inventory`, `GET /api/orders` y `GET /api/returns`.
6. Comparar los identificadores, estados, cantidades y movimientos con la evidencia anterior. Los datos de negocio deben persistir; los tokens de sesión no.

## 7. Lista de control para la grabación

- [ ] Se ve la interfaz y se muestran las respuestas GET, no solo una narración.
- [ ] Tres compradores distintos creados y sus perfiles verificados.
- [ ] Vendedor y bodega creados y verificados.
- [ ] Productos físicos y digitales publicados.
- [ ] Stock físico visible antes de comprar.
- [ ] Cuatro productos en el carrito: dos físicos y dos digitales.
- [ ] Intento de sobrestock rechazado y carrito/inventario sin alteración.
- [ ] Pedido creado y GET inmediato.
- [ ] Pago aprobado y GET inmediato.
- [ ] Envíos y transiciones hasta entrega con GET tras cada operación.
- [ ] Devolución parcial de un producto físico de vendedor identificado.
- [ ] Recepción física y reembolso registrados.
- [ ] Los otros tres productos permanecen sin cambios.
- [ ] Reporte de vendedor antes del reinicio.
- [ ] Pruebas de autorización positivas y negativas por rol.
- [ ] Reinicio completo, nuevo login y reporte idéntico en datos persistidos.

## 8. Nota sobre los GET y las rutas

Las rutas y cuerpos POST están implementados en `NexusMarketHttpServer`. Los GET de colección se filtran por rol. Para las pruebas en terminal, conserva el token de cada sesión por separado; no reutilices el token de un rol después de cerrar sesión.

El documento no afirma que el flujo esté completado: cada casilla debe marcarse únicamente después de ejecutarla y observar el resultado real.
