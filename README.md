# auth-service

Microsserviço de autenticação (Java 25 / Spring Boot 4.1). Cadastra usuários via `account-service`, emite JWTs no login e os valida para o gateway. Não tem banco: o token é autocontido (assinado com HS256).

## Fluxo

```
cliente ──POST /auth/login──▶ gateway ──▶ auth ──POST /accounts/login──▶ account
        ◀── Set-Cookie: __store_jwt_token (HttpOnly) + {"token"} ──┘

cliente ──GET /orders (cookie ou Bearer)──▶ gateway ──POST /auth/solve──▶ auth
                                            gateway ──id-account: <id>──▶ order
```

## Estado: boilerplate

| Pronto | Seu (`TODO(auth)`) |
|---|---|
| Rotas HTTP, validação, cookie `HttpOnly`/`Secure`/`SameSite` | Fluxo em [`AuthService`](src/main/java/store/auth/AuthService.java) |
| [`AccountClient`](src/main/java/store/auth/AccountClient.java) (HTTP para o account, timeouts, mapeamento de erros) | Gerar e validar o JWT em [`JwtService`](src/main/java/store/auth/JwtService.java) |
| Carga da chave: recusa iniciar com `JWT_SECRET` < 32 bytes | |
| Actuator, Prometheus, OpenAPI, Docker, Compose, k8s, CI/CD | |

Enquanto não implementados, os métodos respondem **501 Not Implemented**. [`AuthContractTest`](src/test/java/store/auth/AuthContractTest.java) roda contra um account-service simulado e descreve o comportamento esperado (com `@Disabled` até você implementar).

## Contrato HTTP

| Método e caminho | Resultado |
|---|---|
| `POST /auth/register` | 201; corpo `{"name","email","password"}` → conta criada; e-mail repetido 409 |
| `POST /auth/login` | 200; `{"token"}` + cookie `__store_jwt_token`; credenciais erradas 401 |
| `POST /auth/logout` | 204; expira o cookie |
| `GET /auth/me` | 200 com a conta do header `id-account` (definido pelo gateway) |
| `POST /auth/solve` | 200 `{"idAccount"}`; token inválido/expirado 401. **Interno** (o gateway devolve 404) |
| `GET /auth/health-check` | 200 |

## Configuração

| Variável | Padrão | Observação |
|---|---|---|
| `JWT_SECRET` | — (obrigatória) | ≥ 32 bytes. Em produção, num Secret do k8s |
| `JWT_DURATION` | `PT1H` | Duração ISO-8601 |
| `JWT_ISSUER` | `store-auth` | |
| `JWT_COOKIE_SECURE` | `true` | `false` só em HTTP local |
| `JWT_COOKIE_SAME_SITE` | `Lax` | |
| `ACCOUNT_URL` | `http://account:8080` | |

## Rodar

```bash
mvn -B -ntp verify
# ou, sem Maven/JDK 25 local:
docker run --rm -v "$PWD":/w -w /w maven:3.9-eclipse-temurin-25 mvn -B -ntp verify

python3 scripts/init-env.py          # gera JWT_SECRET aleatório no .env (uma vez)
docker network create store-dev      # uma vez
docker compose up --build -d --wait  # suba o account-service na mesma rede
```

## CI/CD (GitHub Actions)

Igual aos outros serviços: **verify** em todo push/PR; **deploy** para o ECR `auth-service` e EKS em push na `main`, quando a variável de repositório `AWS_ROLE_ARN` existe (OIDC, sem chave AWS no GitHub).

### Preparação única no cluster

```bash
kubectl -n store apply -f k8s/deployer-rbac.yaml
cp k8s/config.example.yaml k8s/config.local.yaml && kubectl -n store apply -f k8s/config.local.yaml
printf 'JWT_SECRET=%s\n' "$(openssl rand -base64 48)" |
  kubectl -n store create secret generic auth-secrets --from-env-file=/dev/stdin
```

## Uso de IA

O boilerplate deste repositório (estrutura, configuração, camada HTTP, cliente do account, testes de contrato desabilitados, Docker/k8s/CI) foi gerado com assistência de IA (Claude) e revisado pela equipe. O fluxo de autenticação e o JWT (`TODO(auth)`) são implementados pela equipe. Registre aqui o que cada integrante fez.
