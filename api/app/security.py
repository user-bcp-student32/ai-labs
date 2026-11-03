import os
from typing import Any

import jwt
from jwt.exceptions import InvalidTokenError

JWT_ISSUER = "workshop-auth"
JWT_AUDIENCE = "workshop-api"
JWT_ALGORITHM = "HS256"
JWT_SECRET_ENV_VAR = "WORKSHOP_JWT_SECRET"
MINIMUM_SECRET_BYTES = 32
REQUIRED_CLAIMS = ["iss", "aud", "iat", "exp", "sub", "role"]


def load_token_secret() -> str:
    secret = os.getenv(JWT_SECRET_ENV_VAR)
    if not secret or len(secret.encode("utf-8")) < MINIMUM_SECRET_BYTES:
        raise RuntimeError(
            f"{JWT_SECRET_ENV_VAR} must be configured with at least "
            f"{MINIMUM_SECRET_BYTES} bytes"
        )
    return secret


def verify_token(authorization: str, secret: str) -> dict[str, str]:
    token = authorization.strip()
    if token.lower().startswith("bearer "):
        token = token[7:].strip()
    if not token:
        raise InvalidTokenError("missing token")

    claims: dict[str, Any] = jwt.decode(
        token,
        secret,
        algorithms=[JWT_ALGORITHM],
        issuer=JWT_ISSUER,
        audience=JWT_AUDIENCE,
        options={"require": REQUIRED_CLAIMS},
    )
    username = claims.get("sub")
    role = claims.get("role")
    if not isinstance(username, str) or not username:
        raise InvalidTokenError("missing subject")
    if not isinstance(role, str) or not role:
        raise InvalidTokenError("missing role")
    return {"username": username, "role": role}
