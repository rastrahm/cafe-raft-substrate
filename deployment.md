## Despliegue en producción (3 nodos en PCs distintas)

Este documento resume cómo configurar 3 nodos de Cafe Raft en hosts distintos, definiendo IPs/puertos, variables y servicios para arranque automático.

### Supuestos

- Hosts y puertos HTTP (ajústalos a tu entorno):
  - Nodo 0 → 192.168.1.10:8080
  - Nodo 1 → 192.168.1.11:8080
  - Nodo 2 → 192.168.1.12:8080
- JDK 21 instalado en cada host (por ejemplo `/usr/lib/jvm/java-21-openjdk-amd64`).
- Puerto 8080/TCP abierto entre hosts (y UDP si usas perfil `rpc-udp`).

### Regla clave

- La lista `cluster.properties.nodes` debe ser IGUAL en los 3 nodos y el `nodeId` de cada nodo debe coincidir con su índice en esa lista (0, 1, 2).
- No uses `localhost` en producción; usa IPs o FQDN alcanzables por todos.

---

## Opción A: Ejecución con Gradle (REST, recomendado)

Ejecuta en cada host con su `nodeId` correspondiente y la misma lista de nodos:

### Nodo 0 (host 192.168.1.10)
```bash
export CAFERAFT_JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64
./gradlew :raft-application-spring:bootRun --args="--cluster.properties.nodeId=0 --server.port=8080 --cluster.properties.nodes=http://192.168.1.10:8080,http://192.168.1.11:8080,http://192.168.1.12:8080 --cluster.properties.base-disk=disk-node-0"
```

### Nodo 1 (host 192.168.1.11)
```bash
export CAFERAFT_JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64
./gradlew :raft-application-spring:bootRun --args="--cluster.properties.nodeId=1 --server.port=8080 --cluster.properties.nodes=http://192.168.1.10:8080,http://192.168.1.11:8080,http://192.168.1.12:8080 --cluster.properties.base-disk=disk-node-1"
```

### Nodo 2 (host 192.168.1.12)
```bash
export CAFERAFT_JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64
./gradlew :raft-application-spring:bootRun --args="--cluster.properties.nodeId=2 --server.port=8080 --cluster.properties.nodes=http://192.168.1.10:8080,http://192.168.1.11:8080,http://192.168.1.12:8080 --cluster.properties.base-disk=disk-node-2"
```

Notas:
- Puedes agregar `--server.address=0.0.0.0` si deseas fijarlo explícitamente.
- Mantén `base-disk` diferente por nodo para separar el almacenamiento.

---

## Opción B: Variables de entorno equivalentes

En cada host:
```bash
export CAFERAFT_JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64
export CLUSTER_PROPERTIES_NODES=http://192.168.1.10:8080,http://192.168.1.11:8080,http://192.168.1.12:8080
export CLUSTER_PROPERTIES_NODEID=<0|1|2>
export CLUSTER_PROPERTIES_BASEDISK=disk-node-<id>
./gradlew :raft-application-spring:bootRun --args="--server.port=8080"
```

---

## Modo UDP (opcional)

Además de lo anterior, activa el perfil y define hosts/puertos UDP (iguales para todos):
```bash
export SPRING_PROFILES_ACTIVE=rpc-udp
export UDP_NODES_HOSTS=192.168.1.10,192.168.1.11,192.168.1.12
export UDP_NODES_PORTS=11111,11112,11113
./gradlew :raft-application-spring:bootRun --args="--cluster.properties.nodeId=<0|1|2> --server.port=8080"
```

Abre 11111–11113/UDP en firewall si aplica. Cada nodo utilizará el puerto UDP según su índice en las listas.

---

## Comprobación rápida

En cada host (ajusta IP/puerto):
```bash
curl http://192.168.1.10:8080/actuator/health
curl http://192.168.1.11:8080/actuator/health
curl http://192.168.1.12:8080/actuator/health
```

Dashboard y Swagger del nodo 0:
- `http://192.168.1.10:8080/`
- `http://192.168.1.10:8080/swagger-ui/index.html`

---

## Servicio systemd (arranque automático)

Ejemplo de unidad por nodo (ajusta rutas/IPs/puertos). Crea el archivo `/etc/systemd/system/caferaft-node-0.service` en el host del nodo 0:

```ini
[Unit]
Description=Cafe Raft Node 0
After=network-online.target
Wants=network-online.target

[Service]
Type=simple
User=ubuntu
WorkingDirectory=/home/ubuntu/cafe-raft
Environment=CAFERAFT_JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64
ExecStart=/bin/bash -lc './gradlew :raft-application-spring:bootRun --args="--cluster.properties.nodeId=0 --server.port=8080 --cluster.properties.nodes=http://192.168.1.10:8080,http://192.168.1.11:8080,http://192.168.1.12:8080 --cluster.properties.base-disk=disk-node-0"'
Restart=always
RestartSec=5

[Install]
WantedBy=multi-user.target
```

Repite para `node-1` y `node-2` cambiando `nodeId`, `base-disk` y (si aplica) IP/puerto del servidor.

Comandos systemd:
```bash
sudo systemctl daemon-reload
sudo systemctl enable caferaft-node-0
sudo systemctl start caferaft-node-0
sudo systemctl status caferaft-node-0
```

Si usas UDP, añade al `ExecStart`:
```
--spring.profiles.active=rpc-udp
```
y exporta/envía `UDP_NODES_HOSTS` y `UDP_NODES_PORTS` como `Environment=` en el bloque `[Service]`.

---

## Buenas prácticas

- Mismo orden en `cluster.properties.nodes` en los 3 nodos (define los índices `nodeId`).
- DNS o IPs estables; evita NAT que bloquee comunicación lateral.
- Logs y almacenamiento separados por nodo; monitoreo con Actuator.
- Backups y rotación de logs del directorio `base-disk` según políticas.


