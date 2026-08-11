# Azure Document Intelligence Receipt OCR Setup

Status: local setup guide. Do not commit `.env`.

## Azure Resource

- Resource name used locally: `moneyflow-doc-intelligence`
- Endpoint:

```text
https://moneyflow-doc-intelligence.cognitiveservices.azure.com
```

Use Azure Portal to copy one key from the resource page. Do not paste the key into docs, commits, screenshots, or logs. Rotate the key if it was exposed.

## Required Env

```text
MONEYFLOW_RECEIPT_OCR_PROVIDER=azure_document_intelligence
AZURE_DOCUMENT_INTELLIGENCE_ENDPOINT=https://moneyflow-doc-intelligence.cognitiveservices.azure.com
AZURE_DOCUMENT_INTELLIGENCE_KEY=
AZURE_DOCUMENT_INTELLIGENCE_MODEL_ID=prebuilt-receipt
AZURE_DOCUMENT_INTELLIGENCE_API_VERSION=2024-11-30
MONEYFLOW_RECEIPT_OCR_TIMEOUT_SECONDS=45
MONEYFLOW_RECEIPT_OCR_POLL_INTERVAL_MS=1500
MONEYFLOW_RECEIPT_OCR_MAX_POLL_ATTEMPTS=20
```

Fill `AZURE_DOCUMENT_INTELLIGENCE_KEY` locally from Azure Portal. `.env` is local only.

## Analyze Mode

Receipt sessions use URL mode:

- upload receipt image
- backend stores image metadata and URL
- Azure receives JSON `urlSource`

The provider can also send image bytes when a caller supplies bytes directly through `ReceiptImageInput`.

## Local PowerShell Loader

```powershell
cd D:\MindMirror\MoneyFlow\moneyflow-backend

Get-Content .env | ForEach-Object {
  if ($_ -match '^\s*#') { return }
  if ($_ -match '^\s*$') { return }
  $name, $value = $_ -split '=', 2
  [Environment]::SetEnvironmentVariable($name.Trim(), $value.Trim(), "Process")
}

$env:JAVA_HOME="C:\Program Files\Java\jdk-24.0.2"
$env:PATH="$env:JAVA_HOME\bin;$env:PATH"

.\mvnw.cmd spring-boot:run
```

## Targeted Mock Validation

```powershell
cd D:\MindMirror\MoneyFlow\moneyflow-backend
$env:JAVA_HOME="C:\Program Files\Java\jdk-24.0.2"
$env:PATH="$env:JAVA_HOME\bin;$env:PATH"
.\mvnw.cmd "-Dtest=*AzureDocumentIntelligence*Tests,*ReceiptOcrProvider*Tests,*ReceiptOcrClient*Tests,*ReceiptOcrSmoke*Tests,*ReceiptOcrAzure*Tests,*ReceiptSessionOcrAzure*Tests" test
```

Expected output: targeted tests pass, no real Azure call, no key printed.

## Real Smoke

Real Azure smoke is manual because it needs a real key and an uploaded receipt image URL that Azure can fetch.

1. Fill `AZURE_DOCUMENT_INTELLIGENCE_KEY` in local `.env`.
2. Start backend with the PowerShell loader.
3. Create a receipt session.
4. Upload a small JPEG or PNG receipt.
5. Run `POST /api/workspaces/{workspaceId}/receipt-sessions/{sessionId}/ocr`.

Expected non-secret result summary:

- `ocrStatus=SUCCEEDED`
- `ocrProvider=AZURE_DOCUMENT_INTELLIGENCE`
- `merchantName` may be present
- `totalAmount` may be present
- `receiptDate` may be present
- warnings explain missing fields

## Troubleshooting

| Symptom | Meaning | Fix |
| --- | --- | --- |
| `OCR_NOT_CONFIGURED` | endpoint or key missing | Fill local `.env`. |
| `OCR_PROVIDER_AUTH_FAILED` | wrong key or endpoint | Check Azure key, region/resource endpoint. |
| `OCR_PROVIDER_RATE_LIMITED` | F0 throttling | Wait, reduce retry/batch size. |
| `OCR_PROVIDER_BAD_REQUEST` | unsupported URL/file | Use small JPEG/PNG/PDF and reachable URL. |
| `OCR_PROVIDER_TIMEOUT` | Azure did not finish in time | Increase attempts or retry with smaller image. |
| `OCR_IMAGE_NOT_ACCESSIBLE` | no stored URL/bytes | Upload image with storage enabled. |

F0 caution: keep images small, avoid broad batch tests, and do not run loops against the live provider.

## Secret Safety

- Never commit `.env`.
- Never print `AZURE_DOCUMENT_INTELLIGENCE_KEY`.
- Do not include the key in URL query strings.
- Provider sends the key only through `Ocp-Apim-Subscription-Key`.
- Rotate the key if it appears in chat, logs, commits, or screenshots.
