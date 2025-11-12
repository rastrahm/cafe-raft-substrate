#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
LOG_DIR="$PROJECT_ROOT/logs"
RUN_DIR="$PROJECT_ROOT/run"

APP_MODULE=":raft-application-spring"

# JAVA_HOME fijo solicitado
EFFECTIVE_JAVA_HOME="/usr/lib/jvm/java-21-openjdk-amd64"

# Intentar autodetectar un JDK 21 si no está definido
select_java_home() {
  if [[ -n "$EFFECTIVE_JAVA_HOME" && -x "$EFFECTIVE_JAVA_HOME/bin/java" ]]; then
    return 0
  fi

  local candidates=(
    "/usr/lib/jvm/java-21-openjdk-amd64"
    "/usr/lib/jvm/java-21-openjdk"
    "/usr/lib/jvm/temurin-21-jdk-amd64"
    "/usr/lib/jvm/temurin-21-jdk"
    "/Library/Java/JavaVirtualMachines/temurin-21.jdk/Contents/Home"
    "/Library/Java/JavaVirtualMachines/zulu-21.jdk/Contents/Home"
  )

  # Intentar derivar desde update-alternatives
  if command -v update-alternatives >/dev/null 2>&1; then
    local alt
    alt="$(update-alternatives --list java 2>/dev/null | head -n1 || true)"
    if [[ -n "$alt" ]]; then
      # alt típico: /usr/lib/jvm/java-21-openjdk-amd64/bin/java
      local home
      home="$(dirname "$(dirname "$alt")")"
      candidates=("$home" "${candidates[@]}")
    fi
  fi

  # Intentar SDKMAN (java current)
  if [[ -s "$HOME/.sdkman/candidates/java/current/bin/java" ]]; then
    candidates=("$HOME/.sdkman/candidates/java/current" "${candidates[@]}")
  fi

  for c in "${candidates[@]}"; do
    if [[ -x "$c/bin/java" ]]; then
      # Verificar versión 21+
      local ver
      ver="$("$c/bin/java" -version 2>&1 | head -n1)"
      local major
      major="$(echo "$ver" | sed -E 's/.*"([0-9]+)(\.[0-9]+)*".*/\1/')"
      if [[ -n "$major" && "$major" -ge 21 ]]; then
        EFFECTIVE_JAVA_HOME="$c"
        export EFFECTIVE_JAVA_HOME
        return 0
      fi
    fi
  done
  return 1
}

mkdir -p "$LOG_DIR" "$RUN_DIR"

usage() {
  cat <<EOF
Uso: $0 <comando>

Comandos:
  start-rest      Inicia 3 nodos (REST) en puertos 8080,8081,8082
  start-udp       Inicia 3 nodos (UDP)  en puertos 8080,8081,8082
  stop            Detiene los 3 nodos si están corriendo
  status          Muestra estado HTTP de los nodos
  open            Abre Dashboard y Swagger del nodo 0

Variables de entorno opcionales:
  NODES           Lista de puertos HTTP (por defecto: 8080,8081,8082)
  UDP_PORTS       Lista de puertos UDP (por defecto: 11111,11112,11113)
EOF
}

require_java() {
  local min_major=21
  # Preferir el java de EFFECTIVE_JAVA_HOME si está definido
  local java_bin
  if [[ -n "$EFFECTIVE_JAVA_HOME" && -x "$EFFECTIVE_JAVA_HOME/bin/java" ]]; then
    java_bin="$EFFECTIVE_JAVA_HOME/bin/java"
  else
    java_bin="java"
  fi
  if ! command -v "$java_bin" >/dev/null 2>&1; then
    echo "Java no está instalado o no es accesible. Instala JDK $min_major+ o define CAFERAFT_JAVA_HOME. Ejemplos:"
    echo "  sudo apt update && sudo apt install -y openjdk-21-jdk"
    echo "  # o con SDKMAN: curl -s https://get.sdkman.io | bash && source \"$HOME/.sdkman/bin/sdkman-init.sh\" && sdk install java 21.0.4-tem"
    echo "  # o export CAFERAFT_JAVA_HOME=/ruta/a/jdk-21"
    exit 1
  fi
  local ver_str
  ver_str="$("$java_bin" -version 2>&1 | head -n1)"
  # Extraer major
  # Formatos posibles: openjdk version \"21.0.4\" ... | openjdk version \"11.0.28\" ...
  local major
  major="$(echo "$ver_str" | sed -E 's/.*"([0-9]+)(\.[0-9]+)*".*/\1/')"
  if [[ -z "$major" ]]; then
    echo "No se pudo determinar la versión de Java desde: $ver_str"
    exit 1
  fi
  if (( major < min_major )); then
    echo "Se requiere JDK $min_major+ (detectado: $ver_str)."
    echo "Solución rápida en Ubuntu/Debian:"
    echo "  sudo apt update && sudo apt install -y openjdk-21-jdk && sudo update-alternatives --config java"
    echo "o usa SDKMAN para instalar/seleccionar Java 21,"
    echo "o exporta CAFERAFT_JAVA_HOME apuntando a un JDK 21 y reintenta."
    exit 1
  fi
}

gradlew() {
  (cd "$PROJECT_ROOT" && env JAVA_HOME="$EFFECTIVE_JAVA_HOME" ./gradlew "$@")
}

start_nodes() {
  local mode="$1" # rest | udp
  local nodes_ports="${NODES:-8080,8081,8082}"
  IFS=',' read -r -a http_ports <<<"$nodes_ports"

  local udp_ports_csv="${UDP_PORTS:-11111,11112,11113}"
  IFS=',' read -r -a udp_ports <<<"$udp_ports_csv"

  echo "Iniciando nodos en modo: $mode"

  for idx in 0 1 2; do
    local port="${http_ports[$idx]}"
    local args=("--cluster.properties.nodeId=$idx" "--server.port=$port" "--spring.aot.enabled=false")
    local profile=""

    if [[ "$mode" == "udp" ]]; then
      profile="--spring.profiles.active=rpc-udp"
      # Exportar variables UDP para el proceso si fuera necesario
      export UDP_NODES_HOSTS="localhost,localhost,localhost"
      export UDP_NODES_PORTS="$udp_ports_csv"
    fi

    local log_file="$LOG_DIR/node-$idx.log"
    local pid_file="$RUN_DIR/node-$idx.pid"

    echo "- Nodo $idx -> puerto $port (logs: $log_file)"
    # Lanzar en background y registrar PID
    if [[ -n "$EFFECTIVE_JAVA_HOME" ]]; then
      (cd "$PROJECT_ROOT" && nohup env JAVA_HOME="$EFFECTIVE_JAVA_HOME" ./gradlew $APP_MODULE:bootRun --args="${args[*]} $profile" >"$log_file" 2>&1 & echo $! >"$pid_file")
    else
      (cd "$PROJECT_ROOT" && nohup ./gradlew $APP_MODULE:bootRun --args="${args[*]} $profile" >"$log_file" 2>&1 & echo $! >"$pid_file")
    fi
    # Pequeña espera para evitar contención del daemon de Gradle
    sleep 2
  done

  echo "Esperando a que los nodos respondan..."
  verify_http 30
}

stop_nodes() {
  local ok=0
  for idx in 0 1 2; do
    local pid_file="$RUN_DIR/node-$idx.pid"
    if [[ -f "$pid_file" ]]; then
      local pid
      pid="$(cat "$pid_file" || true)"
      if [[ -n "${pid:-}" ]] && kill -0 "$pid" 2>/dev/null; then
        echo "Deteniendo nodo $idx (PID $pid)"
        kill "$pid" || true
        ok=1
      fi
      rm -f "$pid_file"
    fi
  done
  if [[ "$ok" -eq 0 ]]; then
    echo "No hay PIDs registrados; intentando matar procesos bootRun residuales..."
    pkill -f "GradleDaemon.*raft-application-spring" 2>/dev/null || true
    pkill -f "org.springframework.boot.loader.launch.JarLauncher" 2>/dev/null || true
  fi
  echo "Listo."
}

verify_http() {
  local retries="${1:-20}"
  local nodes_ports="${NODES:-8080,8081,8082}"
  IFS=',' read -r -a http_ports <<<"$nodes_ports"

  for idx in 0 1 2; do
    local port="${http_ports[$idx]}"
    local url="http://localhost:$port/actuator/health"
    local attempt=0
    echo "Verificando nodo $idx en $url"
    until curl -fsS "$url" >/dev/null 2>&1; do
      attempt=$((attempt+1))
      if [[ $attempt -ge $retries ]]; then
        echo "  - Nodo $idx NO responde tras $retries intentos"
        break
      fi
      sleep 1
    done
    if [[ $attempt -lt $retries ]]; then
      echo "  - Nodo $idx OK"
    fi
  done
}

status_nodes() {
  verify_http 1
}

open_ui() {
  local port="${NODES:-8080,8081,8082}"
  port="${port%%,*}"
  echo "Dashboard: http://localhost:$port/"
  echo "Swagger:   http://localhost:$port/swagger-ui/index.html"
}

case "${1:-}" in
  start-rest)
    select_java_home || true
    require_java
    start_nodes "rest"
    ;;
  start-udp)
    select_java_home || true
    require_java
    start_nodes "udp"
    ;;
  stop)
    stop_nodes
    ;;
  status)
    status_nodes
    ;;
  open)
    open_ui
    ;;
  *)
    usage
    exit 1
    ;;
esac


