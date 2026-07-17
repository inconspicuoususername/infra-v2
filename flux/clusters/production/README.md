# clusters/production

Flux entrypoints for the production cluster. This is the `--path` you point
`flux bootstrap` at.

- `flux-system/` — created by `flux bootstrap` (GitRepository + flux controllers).
  Do not hand-edit; it's machine-managed.
- `infrastructure.yaml` — Kustomizations for platform controllers, then configs.
- `apps.yaml` — Kustomization for workloads; depends on infra; enables SOPS.

Reconcile order: `infra-controllers` → `infra-configs` → `apps`.
