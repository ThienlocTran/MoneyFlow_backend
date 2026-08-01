# Receipt OCR External HTTP Contract

## Request

MoneyFlow sends:

`POST {MONEYFLOW_RECEIPT_OCR_SERVICE_URL}/ocr/receipts`

If the configured URL already ends with `/ocr/receipts`, it is used as-is.

Content type:

`multipart/form-data`

Parts:

- `images`: repeated files in original upload order
- `language`: configured language, default `vi`
- `mode`: `receipt`

Limits are enforced before the OCR call:

- max images: `MONEYFLOW_RECEIPT_OCR_MAX_IMAGES`, default `5`
- max bytes per image: `MONEYFLOW_RECEIPT_OCR_MAX_IMAGE_BYTES`, default `5242880`
- allowed content types: `image/jpeg`, `image/png`, `image/webp`
- timeout: `MONEYFLOW_RECEIPT_OCR_TIMEOUT_SECONDS`, default `30`

## Success response

```json
{
  "status": "SUCCEEDED",
  "text": "CO.OPMART\nSữa tươi 25.000\nBánh mì 15.000\nTổng cộng 40.000",
  "pages": [
    {
      "index": 0,
      "text": "CO.OPMART\n...",
      "confidence": 0.91
    }
  ],
  "warnings": []
}
```

MoneyFlow maps success to `ocr.status=SUCCEEDED`, `source=PHOTO_OCR`, then parses `text` into a receipt review draft.

## Failure response

```json
{
  "status": "FAILED",
  "text": null,
  "warnings": [
    {
      "code": "OCR_NO_TEXT_FOUND",
      "message": "No readable text found."
    }
  ]
}
```

Unknown provider warning codes are mapped to `RECEIPT_OCR_FAILED`. MoneyFlow returns no candidate.

## MoneyFlow warning codes

- `RECEIPT_OCR_NOT_CONFIGURED`
- `RECEIPT_OCR_FAILED`
- `RECEIPT_OCR_TEXT_EMPTY`
- `RECEIPT_OCR_SERVICE_UNAVAILABLE`
- `RECEIPT_OCR_TIMEOUT`
- `RECEIPT_TEXT_REQUIRED_WHEN_OCR_DISABLED`

## Safety

MoneyFlow does not send credentials by default. If an OCR service needs auth, terminate that concern at a private service boundary or add a reviewed backend-only config later. Do not put secrets in the service URL.

Tests use mock HTTP clients and the deterministic `mock` provider. They do not call a real OCR service.
