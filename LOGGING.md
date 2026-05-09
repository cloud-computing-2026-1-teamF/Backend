# Backend API Logging Convention

## Goals

Every backend API request must leave a complete console trail that is useful during local development, staging debugging, and production incident review.

## Required Fields

All API lifecycle logs include:

- `time`: ISO-8601 event time.
- `traceId`: request correlation id. The backend accepts `X-Trace-Id` or generates one.
- `event`: stable machine-readable event name.
- `method`, `path`, `query`: HTTP request target.
- `status`, `outcome`, `durationMs`: response summary when the request finishes.
- `clientIp`, `userAgent`, `contentType`: request context where available.

Error logs additionally include:

- `code`: public API error code.
- `message`: public API error message.
- `details`: validation or domain-specific details.
- `exception`: JVM exception class.
- stack trace: logged through SLF4J with the exception object.

## Event Names

- `http.request.started`: emitted before controller dispatch.
- `http.request.finished`: emitted after every completed request.
- `http.request.aborted`: emitted when an exception escapes the filter chain.
- `http.error.handled`: emitted by the global exception handler before returning an error response.

## Levels

- `INFO`: request started and 2xx/3xx responses.
- `WARN`: handled 4xx responses and validation failures.
- `ERROR`: 5xx responses, unexpected exceptions, and aborted requests.

## Trace Id Rules

- Incoming `X-Trace-Id` is reused only when it matches `^[A-Za-z0-9_.:-]{6,120}$`.
- Otherwise the backend generates a `req_*` id.
- `X-Trace-Id` is always returned to the client.
- `traceId` is stored in MDC, so normal framework logs also include it.

## Safety Rules

- Do not log access tokens, refresh tokens, passwords, or full request bodies.
- Prefer public error codes and sanitized messages in structured fields.
- Attach exceptions to SLF4J calls so stack traces are preserved.
