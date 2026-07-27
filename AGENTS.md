# Repository instructions

## Secret-handling rules

- Never commit API tokens, passwords, private keys, signing credentials, or production configuration values.
- Never read, print, log, document, or expose secret values.
- Never include real secrets in tests, examples, screenshots, comments, prompts, or generated files.
- Use placeholders in committed configuration examples.
- Before committing changes, inspect all staged files for credentials and tokens.
- Production credentials must be supplied through local Gradle properties or CI secrets.
