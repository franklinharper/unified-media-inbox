# Web Signup Design

## Goal

Add open self-service account creation to the web app so a new user can create an account, receive a session immediately, and enter the feed without pre-seeded credentials.

## Approach

Expose a public `POST /api/auth/sign-up` route on the server that creates a user and returns the same authenticated session payload as sign-in. Extend the shared web auth repository and auth state with a matching `signUp(email, password)` operation, then update the existing login screen into a single auth screen with both sign-in and create-account actions.

## API Behavior

- `POST /api/auth/sign-up` accepts the same JSON payload as sign-in: `email` and `password`
- Successful signup creates the user, creates a session, and returns `AuthSessionResponse`
- Duplicate email returns `409 Conflict`
- Malformed request returns `400 Bad Request`

## UI Behavior

- The existing auth screen becomes a combined auth screen
- Users can either sign in or create an account from the same form
- Successful signup signs the user in immediately
- Submission disables both auth actions
- Duplicate-email and auth failures display inline messages

## Testing

- Server tests for signup success and duplicate-email rejection
- Shared tests for remote signup request/response mapping
- Compose tests for signup action dispatch and auth-state transitions
