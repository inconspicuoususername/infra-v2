# backup-operator

Turns `Backup` custom resources into borgmatic CronJobs that archive to a borg
repository (the Hetzner Storage Box). Java Operator SDK, built with Maven.

- `make test` - unit tests (rendering; no cluster needed)
- `make crds` - regenerate `deploy/crds.yaml` from the Java types
- `make image` - push `registry.aquilla.dev/davud/backup-operator:<pom version>` (Jib) and set that tag in `deploy/`
- `make runner-image` - push the borgmatic runner image the CronJobs use (`runner/`)

`deploy/` is a kustomize base (CRDs, RBAC, Deployment) that Flux pulls in from
`flux/infrastructure/controllers`. Cluster-specific pieces stay in `flux/`: the
`backup-system` namespace and source secrets (`infrastructure/base`) and the
`storagebox` BackupRepository (`infrastructure/configs`).

Design notes and gotchas are in the repo's CLAUDE.md, under Backups.
