# Transfer Protocol v1

Plain HTTP over the local network. Every request except `GET /v1/info` and `POST /v1/pair` must
be signed by a paired device.

There is no TLS yet, so **bodies and filenames are readable by anyone sniffing the network**.
TLS needs a different embedded server (FlyingFox has no TLS support) and is tracked separately.

Signing prevents unauthorised and replayed uploads **from an attacker who did not observe
pairing**. Pairing itself returns the secret over plain HTTP, so anyone capturing that single
exchange can forge every signed request afterwards, in both directions. Nothing downstream
recovers from that, and the keystore and Keychain hardening protects a secret that was already
on the wire. Only TLS closes it. Pair on a network you trust.

The receiver advertises itself via Bonjour / DNS-SD:

```text
service type: _androidphototransfer._tcp
TXT: receiverId=<uuid> protocolVersion=1
```

`receiverId` lets a sender pick the right stored pairing straight from discovery. A manually
entered address has no TXT record, so the sender reads `receiverId` from `GET /v1/info` first.

`receiverId` is public: it is broadcast in cleartext and served unauthenticated. Being paired
with a `receiverId` therefore says nothing about whatever is answering on a given address, so
the sender makes the receiver prove it holds the pairing secret before sending anything. See
[Proving the receiver](#proving-the-receiver).

## Pairing

Pairing requires two independent things: the six-digit code, which proves the sender can see
the Mac's screen, and an explicit approval on the Mac, which proves a human is present.

1. The user clicks **Pair a Device** on the Mac, which shows a six-digit code valid for 3 minutes.
2. The phone posts the code to `POST /v1/pair`.
3. The receiver compares the code in constant time. A wrong code counts as a failed attempt, and a
   source that runs out of attempts is refused with `429`. The code stays valid, because whoever is
   guessing is not the person reading it. A correct code is consumed immediately, so it is single use.
4. The receiver then holds the request open while the user approves or denies the named device.
5. On approval, the receiver generates a 32-byte secret, stores it in the login Keychain, and
   returns it with an opaque `deviceToken`.

The sender imports the secret as a non-extractable Android keystore HMAC key, so the raw secret
is not recoverable from app storage afterwards.

### POST /v1/pair

Request:

```json
{
  "protocolVersion": 1,
  "deviceId": "3f2a...",
  "deviceName": "Google Pixel 8",
  "pairingCode": "418273"
}
```

Response `200`:

```json
{
  "receiverId": "f923...",
  "receiverName": "Hamid's MacBook",
  "deviceToken": "9c1e...",
  "secretBase64": "Zm9vYmFy..."
}
```

| Status | Meaning |
| --- | --- |
| `400` | Unsupported `protocolVersion` |
| `401` | No pairing window open, or the code was wrong or expired |
| `403` | The user denied the device |
| `429` | Too many attempts from this source, or too many overall in the last minute |
| `504` | Nobody answered the prompt within 60 seconds |

`504` is deliberate. HTTP clients treat `408` as retryable and resend it automatically, which
would re-prompt using a code that has already been consumed.

The sender refuses the response unless `receiverId` matches the receiver it set out to pair with,
and it will not replace a pairing it already holds for that `receiverId` without asking first. The
responder chooses which id the credentials are stored under, so without those two checks a device
on the network could answer for a receiver it has nothing to do with and quietly displace its
pairing. See [Pairing to an impostor](#known-gaps) for what remains open.

## Request signing

Signed requests carry four headers:

```http
X-PT-Device: <deviceToken>
X-PT-Timestamp: <unix seconds>
X-PT-Nonce: <base64 of 16 random bytes>
X-PT-Signature: <base64 HMAC-SHA256>
```

The signature is HMAC-SHA256 over this canonical string, using the paired secret:

```text
<METHOD>\n<path>\n<timestamp>\n<nonce>\n<body sha256, lower-case hex>
```

For example:

```text
POST
/v1/transfers
1700000000
test-nonce
e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855
```

With a secret of 32 `0x07` bytes, that signs to `m6f0JxKN3W67v2Hm+kcDL8TizBjjtdOiiyifGCYhn9s=`.
Both platforms assert this vector in their unit tests, so a change to the canonical string on one
side fails the build rather than silently breaking pairing.

The receiver rejects a signed request with `401` when the device token is unknown, the timestamp
is more than 5 minutes from its own clock, the nonce was already used inside that window, or the
signature does not match. It does not say which, so a caller learns nothing from probing.

**File uploads sign the empty-body hash.** The receiver streams an upload straight to disk and
never holds it in memory, so neither side can hash it without reading the photo twice. Method,
path, timestamp, and nonce are still covered, so an upload cannot be forged or replayed, but body
integrity waits for `X-Content-SHA256` (see below).

## Proving the receiver

Signing authenticates the sender to the receiver. On its own it leaves the reverse open: anything
on the network can read a `receiverId` off Bonjour, advertise the same one, and be treated as a
receiver the sender already paired with, with no pairing prompt and nothing in the UI to tell it
apart. So every signed response carries the receiver's own proof:

```http
X-PT-Receiver-Signature: <base64 HMAC-SHA256>
```

over a string in a namespace of its own, bound to the nonce from the request it answers:

```text
PT-RESPONSE-v1\n<METHOD>\n<path>\n<nonce>
```

The `PT-RESPONSE-v1` prefix means a captured request signature can never be presented as a proof,
and the nonce means a captured proof cannot be replayed. Only a holder of the pairing secret can
produce one.

With a secret of 32 `0x07` bytes, `POST /v1/verify` with nonce `test-nonce` proves to
`xRGJtiA1mw6eZBFsk9HYA8N/NTpSKr5pMTNEmia0lpU=`. Both platforms assert this vector too.

The sender checks the proof on every signed response, and calls `POST /v1/verify` before it sends
anything at all, so a failure costs no photos and no filenames. A response without a valid proof
is treated as impersonation rather than a transient error: retrying cannot help.

What the proof does **not** stop is a relay. An attacker that advertises the real `receiverId` can
forward each signed request to the real Mac verbatim and hand back the genuine proof it gets. The
phone is satisfied, the Mac stores the photos, and nothing looks wrong on either end, while the
relay has kept a copy of every byte. No pairing capture and no user mistake is needed: the proof
covers method, path and nonce, not the connection it arrived on and not the response body. Uploads
sign the empty-body hash, so a relay can even substitute the bytes the Mac receives.

So the proof is narrower than it looks. It stops an impostor that cannot reach the real receiver
from being taken for it, and it stops a captured proof from being replayed. It does not give the
sender any assurance about who is carrying the traffic. Only TLS does that.

It also does not survive an attacker who captured the pairing exchange, since they hold the secret
and can produce proofs of their own.

## Limits

| Limit | Value | Why |
| --- | --- | --- |
| Buffered request body | 1 MiB | The signature covers the body hash, so authorization cannot happen before the body is read. The declared length is checked first instead, and an oversized or unspecified length is refused with `413` before a byte is buffered. That is what stops an unauthenticated caller from exhausting memory. In practice this caps a manifest at roughly 10,000 photos, so the sender turns that `413` into "send them in smaller batches" rather than showing a status code. |
| Upload size | the manifest `size`, else 2 GiB | Enforced while streaming, so a paired device cannot fill the disk by sending more than it declared. |
| Filename | image extensions only | The app is not sandboxed. If the destination is the home folder, an unrestricted write is code execution on the next shell. Dotfiles, extensionless names, and anything ending in a non-image extension are refused with `415`. |
| Pairing attempts | 5 per source, 30 per minute overall, 1 s apart | Guessing has to be bounded, but an attempt cap that voids the code hands anyone on the network a way to stop the user pairing at all. Exhausted limits are refused with `429` and the code stays valid; the overall cap slides over a minute so it recovers on its own. |

## Redirects

There are none. A sender must not follow `3xx`, because a redirect is a receiver-controlled
way to have the photo body re-sent somewhere else, and a client that restricts itself to the
local network usually applies that restriction once per call rather than per hop. The Android
sender disables redirect following outright, and also judges every hop against the local-only
rule so the restriction survives if that setting is ever changed.

## Endpoints

### GET /v1/info

Identifies the receiver. Unsigned, so a sender can discover `receiverId` before pairing.

Response `200`:

```json
{
  "protocolVersion": 1,
  "receiverId": "f923...",
  "receiverName": "Hamid's MacBook"
}
```

Because it is unsigned, nothing here is trustworthy on its own. `POST /v1/verify` is what
establishes that the responder is the paired receiver.

### POST /v1/verify

Confirms mutual identity before anything is sent. Signed, empty body.

- `204`: signed by a paired device, and the response carries `X-PT-Receiver-Signature`.
- `401`: not signed by a paired device.

A `204` whose proof does not check out means the address is answering for a `receiverId` it does
not own. The sender stops there.

### POST /v1/transfers

Creates a transfer session from a manifest. Signed, and the body is covered by the signature.

The transfer belongs to the device that created it. Another paired device gets `404` on every
route for it, rather than `403`, so it cannot learn that the transfer exists.

Request:

```json
{
  "protocolVersion": 1,
  "files": [
    {
      "id": "file-1",
      "name": "IMG_20260802_173201.jpg",
      "mediaType": "image/jpeg",
      "size": 4837912
    }
  ]
}
```

`size` may be `null` when the provider does not report it.

Response `201`:

```json
{ "transferId": "019234af-428d-712c-a0a1-857571902f18" }
```

- `413`: the body's declared length exceeds 1 MiB, or it declares none.
- `415`: a manifest entry names something that is not an image.

### PUT /v1/transfers/{transferId}/files/{fileId}

Streams one file body. `Content-Type` matches the manifest `mediaType`. Signed over the
empty-body hash, as described above.

- `200`: file received and moved into the destination folder.
- `401`: not signed by a paired device.
- `404`: unknown transfer or file id, or the transfer belongs to another device.
- `409`: file already received.
- `413`: the body exceeded the size declared in the manifest.
- `415`: the manifest entry does not name an image.

The receiver streams to a temporary file. It only moves the file into the destination folder after
the body completes. Filenames are sanitized to their last path component. Collisions get a numeric
suffix: `IMG_1234 (1).jpg`.

### POST /v1/transfers/{transferId}/complete

Marks the transfer finished. Signed.

Response `200`:

```json
{ "receivedFiles": 3 }
```

### GET /v1/transfers/{transferId}

Returns transfer status. Signed.

Response `200`:

```json
{
  "transferId": "0192...",
  "state": "receiving",
  "receivedFiles": 1,
  "totalFiles": 3
}
```

## Known gaps

Things this version does not defend against, stated plainly rather than left implied:

| Gap | Consequence | What closes it |
| --- | --- | --- |
| Pairing returns the secret in cleartext | An attacker who captures that one exchange can impersonate either side afterwards | TLS |
| No confidentiality | Photo bytes and filenames are readable on the network | TLS |
| Pairing to an impostor | A device answering `POST /v1/pair` can accept any code and issue its own secret. Nothing in the response proves the responder knew the code. The sender's identity check and its prompt before replacing a pairing limit the damage but do not detect it; the tell is that the Mac never shows an approval prompt | Binding the response to the pairing code (HMAC or SPAKE2), or TLS with the receiver pinned at pairing time |
| A relay between the phone and the Mac | Every request and its proof can be forwarded to the real receiver, so the transfer succeeds while the relay keeps the photos | TLS |
| The receiver name is public | `Host.current().localizedName` is usually a person's name, and it goes out over Bonjour and unsigned `GET /v1/info` | A configurable name |
| The server binds every interface | On shared Wi-Fi every guest can reach `POST /v1/pair` | Binding to a chosen interface |

## Reserved for later versions

- TLS with certificate pinning, which requires replacing the embedded HTTP server
- `X-Content-SHA256` header and checksum verification, which would also let uploads sign their body
- `Content-Range` chunked, resumable uploads
- Revocation pushed to the sender, rather than the sender discovering it via `401`
