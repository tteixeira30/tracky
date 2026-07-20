#!/bin/sh
# Espera que a base de dados aceite ligações antes de arrancar o Spring Boot.
# O backend (Hibernate com ddl-auto=update) precisa de ligar à DB no arranque;
# se ela estiver momentaneamente indisponível (deploys, reinícios de container),
# o boot falha e o container fica Exited -> /api dá 502. Este wait corre em
# TODOS os arranques (incluindo reinícios via restart policy), ao contrário do
# depends_on do compose, que só ordena o primeiro `up`.
set -e

host="${DB_WAIT_HOST:-db}"
port="${DB_WAIT_PORT:-5432}"
timeout="${DB_WAIT_TIMEOUT:-60}"

echo "A aguardar pela base de dados em ${host}:${port} (máx ${timeout}s)..."
i=0
until nc -z "$host" "$port" 2>/dev/null; do
  i=$((i + 1))
  if [ "$i" -ge "$timeout" ]; then
    echo "AVISO: DB não respondeu em ${timeout}s; a arrancar mesmo assim."
    break
  fi
  sleep 1
done

echo "Base de dados alcançável — a arrancar o backend."
exec java -jar app.jar
