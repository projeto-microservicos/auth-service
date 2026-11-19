# auth-service

Microsserviço de autenticação (Java 25 / Spring Boot 4.1). Cadastra usuários via `account-service`, emite JWTs no login e os valida para o gateway. Não tem banco: o token é autocontido (assinado com HMAC).

## Fluxo

```
cliente ──POST /auth/login──▶ gateway ──▶ auth ──POST /accounts/login──▶ account
        ◀── Set-Cookie: __store_jwt_token (HttpOnly) + {"token"} ──┘

cliente ──GET /orders (cookie ou Bearer)──▶ gateway ──POST /auth/solve──▶ auth
                                            gateway ──id-account: <id>──▶ order
```

## Estado: implementado (17/11/2026)

Login com usuário e senha, no estilo OAuth2, sem provedores externos:

- **Access token** (JWT HS256, `JWT_DURATION`, 15 min por padrão): vai em toda requisição, no cookie `__store_jwt_token` ou em `Authorization: Bearer`. O gateway o resolve em `/auth/solve`.
- **Refresh token** (JWT, `JWT_REFRESH_DURATION`, 7 dias): só serve para `POST /auth/refresh`, que devolve um par novo (rotação). Fica no cookie `__store_refresh_token`, restrito ao caminho `/auth/refresh`, ou no corpo, para scripts.
- O claim `token_use` separa os dois: um refresh token nunca autentica uma requisição, e um access token nunca renova. Cada token tem um `jti` aleatório.
- No refresh, a conta é recarregada: uma conta apagada não renova mais (401).
- **Limite conhecido:** os tokens não guardam estado. O logout expira os cookies, mas um token já copiado vale até expirar. Revogação imediata pediria uma lista de `jti` revogados (Redis ou tabela), uma possível próxima etapa.

[`AuthContractTest`](src/test/java/store/auth/AuthContractTest.java) roda contra um account-service simulado: 11 testes, mais 6 no `AuthSmokeTest`.

## Contrato HTTP

| Método e caminho | Resultado |
|---|---|
| `POST /auth/register` | 201; corpo `{"name","email","password"}` → conta criada; e-mail repetido 409 |
| `POST /auth/login` | 200; `{"token","refreshToken","tokenType":"Bearer","expiresIn"}` + cookies `__store_jwt_token` e `__store_refresh_token`; credenciais erradas 401 |
| `POST /auth/refresh` | 200 com um par novo (mesmo formato do login); o refresh token vem do cookie ou de `{"refreshToken"}`; ausente, inválido, expirado, access token no lugar ou conta apagada 401. **Aberto no gateway** |
| `POST /auth/logout` | 204; expira os dois cookies |
| `GET /auth/me` | 200 com a conta do header `id-account` (definido pelo gateway) |
| `POST /auth/solve` | 200 `{"idAccount"}`; token inválido/expirado 401. **Interno** (o gateway devolve 404) |
| `GET /auth/health-check` | 200, sem token; as probes de startup e liveness chamam este endpoint |

## Configuração

| Variável | Padrão | Observação |
|---|---|---|
| `JWT_SECRET` | — (obrigatória) | ≥ 32 bytes. Em produção, num Secret do k8s |
| `JWT_DURATION` | `PT15M` | Vida do access token (ISO-8601) |
| `JWT_REFRESH_DURATION` | `P7D` | Vida do refresh token |
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

O boilerplate deste repositório (estrutura, configuração, camada HTTP, cliente do account, testes de contrato desabilitados, Docker/k8s/CI) foi gerado com assistência de IA (Claude) e revisado pela equipe. O fluxo de autenticação, o JWT e o refresh (`AuthService`, `JwtService`, `/auth/refresh`) e os testes correspondentes foram implementados por decisão do João (D-01, 17/11/2026), escritas pelo agente do João (Claude) e revisadas pelo João antes do commit. O desenho (usuário e senha, JWT, rota de refresh, sem login social) é do João.
