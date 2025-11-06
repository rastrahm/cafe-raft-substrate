## Diagramas de arquitectura

### 1) Secuencia de escritura (comando → commit → respuesta)

```
Cliente             Nodo A (Follower)             Nodo B (Líder)              Nodo C (Follower)
   |                         |                           |                           |
1) |--- POST /balance -----> |                           |                           |
   |                         | no es líder               |                           |
2) |<-- 307/forward -------- | -- reenvía al líder ----> |                           |
   |                         |                           |                           |
3) |--- POST /balance ---------------------------------> |                           |
   |                         |                           | añade entrada al log      |
4) |                         |                           |-- AppendEntries --------> |
   |                         |                           |-- AppendEntries ---------------->|
   |                         |                           |                           |
5) |                         |                           |<- Ack (OK) ---------------|
   |                         |                           |<---------------- Ack (OK) -|
   |                         |                           | (mayoría alcanzada)        |
6) |                         |                           | marca commit, aplica a SM  |
7) |<--------------------------- 200 OK (respuesta) ---- |                           |
   |                         |                           |                           |
8) |                         | aplica commit por índice  |                           |
   |                         | (cuando commit avanza)    |                           |
```

Leyenda:
- Reenvío automático: el seguidor reenvía al líder (paso 2).
- Commit por mayoría: el líder confirma cuando recibe acks de N/2+1 (pasos 5–6).
- SM (máquina de estado): líder aplica tras commit; seguidores aplican al avanzar el índice comprometido.

### 2) Ciclo de elección y heartbeats

Elección (arranque o pérdida de líder)
```
Nodo A (Follower)     Nodo B (Follower)     Nodo C (Follower)
     |   tA expira         |   tB expira         |   tC expira
1)   |---- Timeout ---->   |                    |
2)   |-- Candidate A --    |                    |
3)   |-- RequestVote ----> |                    |<----- RequestVote ----- A
     |                     |-- Voto? ---------->|
     |<----- Voto ---------|                    |
4)   | (mayoría?) Sí  ----> se convierte en LEADER
```

Mantenimiento (heartbeats del líder)
```
Cliente         Líder (B)                 Seguidor (A)            Seguidor (C)
   |                |  cada heartbeatIntervalMs    |                     |
   |                |---- AppendEntries (vacío) --->|                     |
   |                |<----------- Ack --------------|                     |
   |                |---- AppendEntries (vacío) ------------------------->|
   |                |<------------------------------ Ack -----------------|
```

Reelección por ausencia de heartbeats
```
Líder (B) falla
Seguidor A        Seguidor C
   |  no heartbeats           |  no heartbeats
1) |---- Timeout ------------>|
2) |-- Candidate A -----------|
3) |-- RequestVote -----> C   |
   |<----- Voto --------------|
4) | mayoría alcanzada → A es LEADER
```

Empate (split vote) y backoff aleatorio
```
A y C inician a la vez (timeouts distintos aleatorios)
A: pide votos a C      C: pide votos a A
C vota a C             A vota a A
no hay mayoría → nuevos timeouts (randomizados) → uno gana en la siguiente ronda
```


