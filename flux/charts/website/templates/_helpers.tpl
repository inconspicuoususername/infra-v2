{{- define "website.labels" -}}
app: {{ .Release.Name }}
app.kubernetes.io/name: {{ .Release.Name }}
app.kubernetes.io/managed-by: {{ .Release.Service }}
{{- /* No helm.sh/chart label: with reconcileStrategy Revision the chart version
carries the git sha (invalid "+" in labels) and would restart pods on every commit. */}}
{{- end }}

{{/* "/app/.next/cache" -> "rw-app-next-cache" */}}
{{- define "website.volName" -}}
rw-{{ . | trimPrefix "/" | replace "/" "-" | replace "." "" | lower }}
{{- end }}
