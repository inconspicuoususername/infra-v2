{{- define "website.labels" -}}
app: {{ .Release.Name }}
app.kubernetes.io/name: {{ .Release.Name }}
app.kubernetes.io/managed-by: {{ .Release.Service }}
helm.sh/chart: {{ .Chart.Name }}-{{ .Chart.Version }}
{{- end }}

{{/* "/app/.next/cache" -> "rw-app-next-cache" */}}
{{- define "website.volName" -}}
rw-{{ . | trimPrefix "/" | replace "/" "-" | replace "." "" | lower }}
{{- end }}
