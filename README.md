https://docs.developer.singpass.gov.sg/docs/technical-specifications/integration-guide/1.-authorization-request

To implement the Singpass Myinfo v5 flow, it is important to clarify a key cryptographic change: **Singpass v5 has completely deprecated X.509 public key certificates (`.crt` / `.pem` files)**.

Instead, it strictly uses a **JSON Web Key Set (JWKS)** for all client authentication, token encryption, and DPoP signing mechanisms. You will maintain a JSON file containing your public keys hosted on an endpoint or configured via the Singpass Developer Portal.

Below are the exact HTTP request payloads and JSON Web Key (JWK) structures for all four steps.

---

## 0. The Key Framework: Your JWKS Structure

Instead of sending certificates, your application uses private keys from a JWKS like this to sign assertions and decrypt responses:

```json
{
  "keys": [
    {
      "kty": "EC",
      "use": "sig",
      "alg": "ES256",
      "crv": "P-256",
      "kid": "client-sig-key-v5",
      "x": "f83OJ3D2xF...",
      "y": "x_da7Wks1l..."
    },
    {
      "kty": "EC",
      "use": "enc",
      "alg": "ECDH-ES+A256KW",
      "crv": "P-256",
      "kid": "client-enc-key-v5",
      "x": "E9b8xG2k...",
      "y": "Wp8vN1s..."
    }
  ]
}

```

---

## Endpoint Request 1: OIDC Discovery (Metadata Fetch)

This is a standard public `GET` request. No authentication or keys are required to call this endpoint.

### HTTP Request

```http
GET /.well-known/openid-configuration HTTP/1.1
Host: id.singpass.gov.sg
Accept: application/json

```

### Response Example (Truncated)

Your application reads this response to dynamically extract the URLs needed for Requests 2, 3, and 4:

```json
{
  "issuer": "https://id.singpass.gov.sg",
  "pushed_authorization_request_endpoint": "https://id.singpass.gov.sg/fapi/par",
  "token_endpoint": "https://id.singpass.gov.sg/fapi/token",
  "userinfo_endpoint": "https://id.singpass.gov.sg/fapi/userinfo"
}

```

---

## Endpoint Request 2: Pushed Authorization Request (PAR)

This is a backend-to-backend `POST` request to register your authorization parameters before sending the user to login.

### Required Headers & Body

* **`DPoP` Header:** A JWT signed by an *ephemeral, session-specific* EC key pair generated on-the-fly in your memory cache.
* **`client_assertion`:** A short-lived JWT signed using your permanent **corporate signing key** (`use: sig`) from your JWKS.

### HTTP Request

```http
POST /fapi/par HTTP/1.1
Host: id.singpass.gov.sg
Content-Type: application/x-www-form-urlencoded
DPoP: eyJhbGciOiJFUzI1NiIsInR5cCI6ImRwb3Arand0IiwiandrIjp7Imt0eSI6IkVDIiwiY3J2IjoiUC0yNTYifX0...[Ephemeral DPoP JWT]

client_id=MY_REGISTERED_CLIENT_ID
&response_type=code
&scope=openid+user.identity
&redirect_uri=https%3A%2F%2Fmy-app.com%2Fcallback
&state=9b1deb4d-3b7d-4bad-9bdd-2b0d7b3dcb6d
&nonce=6c2ef50e-117c-4c6e-8ff5-f09dfd7008ea
&code_challenge=E9Melhoa2OwvFrGMTJguCH5K1_0_D8j67SzoExXN9YE
&code_challenge_method=S256
&client_assertion_type=urn%3Aietf%3Aparams%3Aoauth%3Aclient-assertion-type%3Ajwt-bearer
&client_assertion=eyJhbGciOiJFUzI1NiIsImtpZCI6ImNsaWVudC1zaWcta2V5LXY1In0...[Corporate Signed JWT]

```

### Response Example

```json
{
  "request_uri": "urn:ietf:params:oauth:request_uri:b070be6a3fd54f59",
  "expires_in": 60
}

```

---

## Endpoint Request 3: Token Request

Executed by your backend after the user successfully authenticates and returns an authorization code via your frontend.

### Critical Requirement

The **`DPoP` header** must be signed by the **exact same ephemeral private key** generated during Request 2.

### HTTP Request

```http
POST /fapi/token HTTP/1.1
Host: id.singpass.gov.sg
Content-Type: application/x-www-form-urlencoded
DPoP: eyJhbGciOiJFUzI1NiIsInR5cCI6ImRwb3Arand0IiwiandrIj...[Same Ephemeral Key signed JWT]

grant_type=authorization_code
&code=SplxlOBeZQQYbYS6WxSbIA
&redirect_uri=https%3A%2F%2Fmy-app.com%2Fcallback
&code_verifier=dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk
&client_id=MY_REGISTERED_CLIENT_ID
&client_assertion_type=urn%3Aietf%3Aparams%3Aoauth%3Aclient-assertion-type%3Ajwt-bearer
&client_assertion=eyJhbGciOiJFUzI1NiIsImtpZCI6ImNsaWVudC1zaWcta2V5LXY1In0...[Fresh Corporate Signed JWT]

```

### Response Example

```json
{
  "access_token": "DPoP_bound_access_token_string_here",
  "token_type": "DPoP",
  "expires_in": 3600,
  "id_token": "eyJhbGciOiJFUzI1NiIsImVuYyI6IkEyNTZHQ00ifQ..."
}

```

---

## Endpoint Request 4: Userinfo Request (Person Info API Replacement)

In Myinfo v5, the legacy Person API is deprecated. You call the standard OIDC Userinfo Endpoint using a `GET` request.

### Critical Requirement

The `Authorization` header passes your token as `DPoP <token>` rather than `Bearer <token>`. You must generate a fresh DPoP signature using that same ephemeral key.

### HTTP Request

```http
GET /fapi/userinfo HTTP/1.1
Host: id.singpass.gov.sg
Authorization: DPoP DPoP_bound_access_token_string_here
DPoP: eyJhbGciOiJFUzI1NiIsInR5cCI6ImRwb3Arand0IiwiandrIj...[Freshly signed with Ephemeral Key]

```

### Response Example

The payload returned is a highly secure JWE (Encrypted JWT). Your backend must use your permanent **corporate encryption private key** (`use: enc`) from your JWKS configuration to decrypt it and reveal the cleartext JSON citizen data.

```text
eyJhbGciOiJFQ0RILUVTK0EyNTZLVyIsImVuYyI6IkEyNTZHQ00iLCJraWQiOiJjbGllbnQtZW5jLWtleS12NSJ9...[Encrypted Payload]

```
