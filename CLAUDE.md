# v2 — k0s + Flux infra

This is a from-scratch rebuild of the personal infra (repo root is the old
Ansible + Docker Compose system, kept for reference). v2 runs everything as a
single-node Kubernetes (k0s) cluster, deployed GitOps-style with Flux, on the
user's own bare metal.

**Status:** scaffold. The user is hand-building/learning from here. Manifests
have NOT been applied or validated against a live cluster. Versions are
placeholders pinned with `TODO: pin`.

## The one rule: three layers, strict boundaries

```
pyinfra  → OS baseline only (host packages, firewall, sysctls, disk mounts)
k0sctl   → cluster bring-up + the ONLY in-cluster floor: a storage provisioner
Flux     → everything else in the cluster, reconciled from git
```

- pyinfra **never** reaches above the OS.
- k0sctl **never** installs apps (only the openebs-hostpath StorageClass).
- The moment the API server is up, **Flux owns the world.** New workloads,
  platform controllers, config — all go through git, not `kubectl apply`.

If you're tempted to put an app in k0sctl or a host package in Flux, stop.

## Layout

```
  johninfra/               # layer 1 (pyinfra): inventory.py, deploy.py
  k0sctl/k0sctl.yaml       # layer 2
  flux/
    clusters/production/   # Flux entrypoints (the `flux bootstrap --path` target)
    infrastructure/
      controllers/         # HelmReleases: traefik, cert-manager, cloudnative-pg
      configs/             # cluster-issuer (Let's Encrypt) etc.
    apps/
      base/<app>/          # per-app manifests
      production/          # overlay (pulls in bases; add patches when staging exists)
  .sops.yaml               # SOPS/age encryption rules
```

Reconcile order is enforced via `dependsOn`:
`infra-controllers` → `infra-configs` → `apps`.

## Conventions

- **One app = one folder** under `apps/base/<name>/` with its own
  `kustomization.yaml` and `namespace.yaml`. Add it to
  `apps/production/kustomization.yaml`.
- **Charts:** `HelmRepository` (in `flux-system`) is defined once in
  `infrastructure/controllers/helmrepositories.yaml`; `HelmRelease`s pin the
  version. Don't pin versions in the HelmRepository.
- **Secrets:** SOPS + age. Each app ships `secret.example.yaml` (plaintext docs,
  NOT applied). Copy to `secret.yaml`, fill, then
  `sops --encrypt --in-place secret.yaml`. `.sops.yaml` only encrypts
  `*.secret.yaml` and only the `data`/`stringData` fields. Flux decrypts via the
  `sops-age` secret in `flux-system`. **Never commit plaintext secrets.**
- **Storage:** `storageClass: openebs-hostpath` (local disk). NEVER put a
  database or p4d `db.*` on network storage — latency kills them.
- **Ingress (HTTP):** Traefik `IngressRoute` (CRD) + a cert-manager `Certificate`
  referencing `letsencrypt-prod`, whose TLS secret the IngressRoute consumes.
- **Single node, no MetalLB:** Traefik binds host ports 80/443/1666 via
  `hostPort` (replicas: 1). Add MetalLB only when a second node appears.

## Patterns worth copying

- **Postgres → CloudNativePG** (`apps/base/authentik/postgres.yaml`): a `Cluster`
  for lifecycle only — provisioning, failover, rolling upgrades. Use it for every
  database. **Not** for backup: barman only targets S3/Azure/GCS, but the backup
  target is a Hetzner Storage Box (SFTP). Backups are logical dumps via borg; see
  **Backups** below.
- **Stateful pet → StatefulSet replicas:1** (`apps/base/perforce/`): for
  single-writer services (p4d). RollingUpdate on a 1-replica STS has no pod
  overlap, so it's safe on shared local storage.
- **Non-HTTP TCP service → `IngressRouteTCP`** with `tls.passthrough: true` on a
  dedicated Traefik entrypoint (p4d on :1666).
- **Backups → borg, not k8up/barman.** Per-app borgmatic CronJobs
  (`apps/base/<app>/backup.yaml`) archive to a Hetzner Storage Box over SFTP. For
  p4d, a CronJob runs `p4 admin checkpoint` first (you can't archive a live
  `db.*`), then borg archives the PVC. See **Backups** below.

## Backups

The agent-scaffolded v2 originally used k8up (restic→S3) for files and CNPG
barman (→S3) for Postgres. Both were ripped out: the backup target is a **Hetzner
Storage Box (SFTP/borg), not S3**, and paying for Hetzner Object Storage was
rejected. CNPG barman only targets S3/Azure/GCS; k8up's Backend has no SFTP;
openebs-hostpath has no volume snapshots. So both scaffolded paths were dead ends.

- **Now:** per-app borgmatic CronJobs in each app's own namespace, reusing the
  existing borg repo + passphrase + SSH key from the old setup. Nightly logical
  dumps, **no PITR** (parity with the old borgmatic + pg_dump, not a regression).
  One repo per app under `…/backups/v2/<app>`.
- **Secrets:** a `borg-credentials` secret (passphrase + SSH key) is duplicated
  per namespace because k8s secrets can't be read across namespaces. The
  plaintext lives once in the ansible vault; it's SOPS-encrypted per app.
- **In progress:** a custom backup operator (`backup-operator/`, kubebuilder)
  that replaces the hand-written CronJobs with a `Backup` CRD mirroring the old
  ansible backup DSL (`stacks.yml` `backups:`). The controller resolves CNPG
  services, projects the borg credential into each namespace (killing the per-app
  secret copy), and generates the CronJobs. Not yet wired into Flux.

Do **not** reintroduce k8up, barman, or any S3 backup path.

## Gotchas / known-unvalidated areas

- Chart value schemas drift — the authentik `envValueFrom`/`envFrom` keys and the
  Traefik `ports` block are the most likely to need adjusting against the actual
  chart version you pin. Verify with `helm show values`.
- Host firewall + CNI can conflict. Fedora Server is firewalld-managed (a raw
  nftables ruleset gets clobbered and isn't even read from `/etc/nftables.conf`
  there), so deploy.py puts the pod/service CIDRs in firewalld's trusted zone.
  Symptom when this is missing: pods Pending is fine but konnectivity fails
  with "No agent available" — webhooks and `kubectl logs` break. Widen the
  trusted sources — don't disable the FW.
- cert-manager HTTP-01 needs Traefik to handle `Ingress` (not just IngressRoute);
  that's why `providers.kubernetesIngress` is enabled in the Traefik values.

## What's NOT migrated

- **mailcow:** dropped — moving to a hosted mail provider (Migadu). Don't try to
  put it in k8s; it's compose-only upstream anyway.
- Old Ansible/compose stacks in the repo root are reference, not active targets.
