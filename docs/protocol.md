# Transfer Protocol v1 (walking-skeleton subset)

Plain HTTP over the local network. No authentication or TLS yet. Those arrive with QR pairing in a later iteration.

The receiver advertises itself via Bonjour / DNS-SD:

```text
service type: _androidphototransfer._tcp
```

The TXT record is unused in v1. The sender resolves the service to a host and port.

## Endpoints

### GET /v1/info

Identifies the receiver. Used as a reachability check before a transfer.

Response `200`:

```json
{
  "protocolVersion": 1,
  "receiverName": "Hamid's MacBook"
}
```

### POST /v1/transfers

Creates a transfer session from a manifest.

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

### PUT /v1/transfers/{transferId}/files/{fileId}

Streams one file body. `Content-Type` matches the manifest `mediaType`.

- `200`: file received and moved into the destination folder.
- `404`: unknown transfer or file id.
- `409`: file already received.

The receiver streams to a temporary file. It only moves the file into the destination folder after the body completes. Filenames are sanitized to their last path component. Collisions get a numeric suffix: `IMG_1234 (1).jpg`.

### POST /v1/transfers/{transferId}/complete

Marks the transfer finished.

Response `200`:

```json
{ "receivedFiles": 3 }
```

### GET /v1/transfers/{transferId}

Returns transfer status.

Response `200`:

```json
{
  "transferId": "0192...",
  "state": "receiving",
  "receivedFiles": 1,
  "totalFiles": 3
}
```

## Reserved for later versions

- `POST /v1/pair` (QR pairing, bearer tokens)
- `X-Content-SHA256` header and checksum verification
- `Content-Range` chunked, resumable uploads
