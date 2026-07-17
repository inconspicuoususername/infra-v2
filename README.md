# infra v2
My personal infrastructure for my home setup. It's a k8 (k0) version of a setup I previously used with Ansible and Docker Compose. It's crap, mostly because I'm trying to figure out how this stuff works.

## Setup after cloning

Enable the pre-commit hook (gitleaks + SOPS encryption guard, see
`.githooks/pre-commit` and `.gitleaks.toml`). `core.hooksPath` is per-clone
config, so this must be re-run in every fresh clone:

```sh
git config core.hooksPath .githooks
```

Requires `gitleaks` on PATH (`pacman -S gitleaks/brew install gitleaks`).