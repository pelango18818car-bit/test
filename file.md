The answer depends on whether your existing **v4 keys** meet the strict **FAPI 2.0 cryptographic requirements** enforced by Singpass v5.

Here is exactly how your JWKS applies to the v5 protocols (**Client Assertion**, **PKCE**, and **DPoP**):

---

### 1. For your Client Assertion (Signing)

**Yes, you can reuse your v4 signing key**, provided it is an **Elliptic Curve (EC)** key.

Singpass v5 strictly enforces Financial-grade API (FAPI 2.0) profiles. Legacy RSA keys (like RS256) are deprecated. Look at your current v4 JWKS file; your signing key **must** have these exact parameters:

* `"kty": "EC"` (Elliptic Curve)
* `"alg": "ES256"`, `"ES384"`, or `"ES512"`
* `"crv": "P-256"`, `"P-384"`, or `"P-521"`
* `"use": "sig"`

If your v4 key is already an EC key on one of those curves, you will sign your v5 `client_assertion` JWTs using this exact same private key, and Singpass will successfully verify them against your pre-existing JWKS.

---

### 2. For PKCE (Proof Key for Code Exchange)

**No keys from your JWKS are used for PKCE.**
PKCE does not use asymmetric cryptographic key pairs. Instead, it relies on a raw, ephemeral cryptographically secure random string (`code_verifier`) and its SHA-256 hash (`code_challenge`) generated on-the-fly inside your code for *each individual user login session*. You do not store or configure PKCE keys in your JWKS.

---

### 3. For DPoP (Demonstrating Proof-of-Possession)

**No, you should NOT use your pre-registered JWKS keys for DPoP.**

This is a critical architectural rule of DPoP (RFC 9449):

* **Your JWKS contains your permanent Client Identity keys**.
* **DPoP proofs must use an ephemeral, transactional key pair**.

#### How DPoP Keying Works in Your Application:

1. When a user clicks "Login", your backend code generates a brand new, lightweight **ephemeral EC key pair** on-the-fly in memory.
2. You sign the DPoP header JWT using this *new ephemeral private key*.
3. You embed the public portion of this *new ephemeral key* directly **inside the DPoP JWT header itself** (`jwk` parameter).
4. Singpass extracts the public key straight from the incoming DPoP header to verify the proof. Singpass does *not* look at your pre-registered corporate JWKS file to validate a DPoP proof.
5. **Crucial Rule:** You must retain this ephemeral key pair in your application's user session state (or memory cache) because **you must use the exact same DPoP key pair across all 3 steps** (PAR Request, Token Request, and Userinfo Retrieval) for that specific user session. Once the user session finishes, throw the key pair away.

---

### Summary Checklist for your JWKS

| Flow Component | Does it use your pre-existing v4 JWKS? | Requirement |
| --- | --- | --- |
| **Client Assertion** | **Yes** (If compliant) | Must be EC (ES256/384/512). |
| **ID Token Decryption** | **Yes** (If compliant) | In v5, ID tokens are *always* encrypted. Ensure your JWKS includes an encryption key (`"use": "enc"`, `"kty": "EC"`). |
| **PKCE** | **No** | Runtime generated SHA-256 strings. |
| **DPoP** | **No** | Runtime generated ephemeral EC keys passed inline inside the request headers. |
