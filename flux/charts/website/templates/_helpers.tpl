{{- /* Immutable on Deployments once created — changing these means recreating them. */}}
{{- define "website.selectorLabels" -}}
app.kubernetes.io/name: {{ .Chart.Name }}
app.kubernetes.io/instance: {{ .Release.Name }}
{{- end }}

{{- define "website.labels" -}}
{{ include "website.selectorLabels" . }}
app.kubernetes.io/managed-by: {{ .Release.Service }}
{{- /* No helm.sh/chart label: with reconcileStrategy Revision the chart version
carries the git sha (invalid "+" in labels) and would restart pods on every commit. */}}
{{- end }}

{{/* "/app/.next/cache" -> "rw-app-next-cache" */}}
{{- define "website.volName" -}}
rw-{{ . | trimPrefix "/" | replace "/" "-" | replace "." "" | lower }}
{{- end }}
