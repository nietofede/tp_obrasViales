# Prueba de punta a punta de ms-obras contra Keycloak y PostgreSQL reales.
# Requisitos: docker compose up -d y ms-obras corriendo en el puerto 8082.
# Se corre desde la raiz del repo (o desde ms-obras). Crea una obra nueva en cada corrida.
& {
  if ((Split-Path -Leaf (Get-Location)) -eq 'ms-obras') { Set-Location .. }
  $base = "http://localhost:8082"
  $kc = "http://localhost:8080/realms/obras-viales/protocol/openid-connect/token"
  $secret = ((Get-Content .env | Where-Object { $_ -match '^SERVICIO_SISTEMA_SECRET=' }) -replace '^SERVICIO_SISTEMA_SECRET=', '').Trim()
  $form = 'application/x-www-form-urlencoded'
  $t = @{}
  foreach ($u in 'planificador','capataz','operario','auditor') {
    $t[$u] = (Invoke-RestMethod -Uri $kc -Method Post -ContentType $form -Body @{ grant_type='password'; client_id='obras-public-client'; username=$u; password="${u}123" }).access_token
  }
  $t['sistema'] = (Invoke-RestMethod -Uri $kc -Method Post -ContentType $form -Body @{ grant_type='client_credentials'; client_id='servicio-sistema'; client_secret=$secret }).access_token
  $script:ok = 0; $script:total = 0

  function Llamar($n, $metodo, $ruta, $quien, $cuerpo, $esperado, $verificar) {
    $script:total++
    $p = @{ Uri = "$base$ruta"; Method = $metodo; UseBasicParsing = $true; ContentType = 'application/json' }
    if ($t[$quien]) { $p['Headers'] = @{ Authorization = "Bearer $($t[$quien])" } }
    if ($cuerpo) { $p['Body'] = $cuerpo }
    $txt = ''
    try {
      $r = Invoke-WebRequest @p
      $sc = [int]$r.StatusCode
      $txt = $r.Content
    } catch {
      $resp = $_.Exception.Response
      $sc = [int]$resp.StatusCode
      $txt = $_.ErrorDetails.Message
      if (-not $txt -and $resp -and ($resp | Get-Member -Name GetResponseStream -ErrorAction SilentlyContinue)) {
        try {
          $lector = New-Object System.IO.StreamReader($resp.GetResponseStream())
          $txt = $lector.ReadToEnd()
          $lector.Close()
        } catch { }
      }
    }
    $j = $null
    if ($txt) { try { $j = $txt | ConvertFrom-Json } catch { } }
    $bien = ($sc -eq $esperado)
    if ($bien -and $verificar) { $bien = [bool](& $verificar $j $txt) }
    if ($bien) { $script:ok++; Write-Host ("OK     #{0} {1} {2} ({3}) -> {4}" -f $n, $metodo, $ruta, $quien, $sc) -ForegroundColor Green }
    else { Write-Host ("FALLA  #{0} {1} {2} ({3}) -> recibido {4}, esperado {5}`n       {6}" -f $n, $metodo, $ruta, $quien, $sc, $esperado, $txt) -ForegroundColor Red }
    return $j
  }

  $obra   = '{"nombre":"Demo Av. Libertad","descripcion":"Prueba de punta a punta"}'
  $obraEd = '{"nombre":"Demo Av. Libertad (editada)","descripcion":"Descripcion corregida"}'
  $tramo  = '{"nombre":"Tramo norte","superficieM2":1000,"latitud":-34.6,"longitud":-58.4}'
  $tramoMal = '{"nombre":"Tramo norte","superficieM2":1000,"latitud":91,"longitud":-58.4}'
  $tramoEd  = '{"nombre":"Tramo norte","superficieM2":2000,"latitud":-34.6,"longitud":-58.4}'
  $repro = '{"nuevaFechaInicio":"2026-12-12"}'

  Llamar 1 POST '/api/obras' 'sin' $obra 401 | Out-Null
  Llamar 2 POST '/api/obras' 'capataz' $obra 403 | Out-Null
  $j = Llamar 3 POST '/api/obras' 'planificador' $obra 201; $obraId = $j.id
  Llamar 4 PUT "/api/obras/$obraId" 'capataz' $obraEd 403 | Out-Null
  Llamar 5 PUT "/api/obras/$obraId" 'planificador' $obraEd 200 { param($j,$x) $x -match 'editada' } | Out-Null
  $j = Llamar 6 POST "/api/obras/$obraId/tramos" 'planificador' $tramo 201; $tramoId = $j.id
  Llamar 7 POST "/api/obras/$obraId/tramos" 'planificador' $tramoMal 400 { param($j,$x) $x -match 'latitud' } | Out-Null
  Llamar 8 POST '/api/obras/99999/tramos' 'planificador' $tramo 404 | Out-Null
  Llamar 9 PUT "/api/obras/tramos/$tramoId" 'planificador' $tramoEd 200 { param($j,$x) $j.superficieM2 -eq 2000 } | Out-Null
  Llamar 10 GET "/api/obras/tramos/$tramoId" 'auditor' $null 200 { param($j,$x) $j.superficieM2 -eq 2000 } | Out-Null
  $fa = '{"tipo":"RELEVAMIENTO","fechaInicioPlanificada":"2026-12-01","fechaFinPlanificada":"2026-12-05"}'
  $j = Llamar 11 POST "/api/obras/tramos/$tramoId/fases" 'planificador' $fa 201; $faseA = $j.id
  $fb = '{"tipo":"BASE","fechaInicioPlanificada":"2026-12-05","fechaFinPlanificada":"2026-12-10","faseAnteriorId":' + $faseA + '}'
  $j = Llamar 12 POST "/api/obras/tramos/$tramoId/fases" 'planificador' $fb 201; $faseB = $j.id
  $fc = '{"tipo":"SUBBASE","fechaInicioPlanificada":"2026-12-11","fechaFinPlanificada":"2026-12-15","faseAnteriorId":' + $faseA + '}'
  Llamar 13 POST "/api/obras/tramos/$tramoId/fases" 'planificador' $fc 409 | Out-Null
  Llamar 14 GET "/api/obras/fases/$faseB" 'auditor' $null 200 { param($j,$x) $x -match '"estado":"PENDIENTE"' -and $x -match '"avance":0' } | Out-Null
  Llamar 15 GET "/api/obras/fases/$faseB/materiales" 'auditor' $null 403 | Out-Null
  Llamar 16 GET "/api/obras/fases/$faseB/materiales" 'capataz' $null 200 { param($j,$x) $x -match '"cantidad":500' } | Out-Null
  Llamar 17 POST "/api/obras/fases/$faseB/iniciar" 'capataz' $null 409 { param($j,$x) $x -match 'RN-01' } | Out-Null
  Llamar 18 POST "/api/obras/internos/fases/$faseB/avances" 'sistema' '{"porcentaje":30}' 409 { param($j,$x) $x -match 'RN-01' } | Out-Null
  Llamar 19 POST "/api/obras/internos/fases/$faseA/avances" 'planificador' '{"porcentaje":50}' 403 | Out-Null
  Llamar 20 POST "/api/obras/internos/fases/$faseA/avances" 'sistema' '{"porcentaje":0}' 400 { param($j,$x) $x -match 'porcentaje' } | Out-Null
  Llamar 21 POST "/api/obras/internos/fases/$faseA/avances" 'sistema' '{"porcentaje":50}' 201 { param($j,$x) $x -match '"estado":"EN_CURSO"' -and $x -match '"avance":50' } | Out-Null
  Llamar 22 POST "/api/obras/internos/fases/$faseA/avances" 'sistema' '{"porcentaje":20}' 422 { param($j,$x) $x -match 'AVANCE' } | Out-Null
  Llamar 23 POST "/api/obras/internos/fases/$faseA/avances" 'sistema' '{"porcentaje":100}' 201 { param($j,$x) $x -match '"estado":"COMPLETADA"' } | Out-Null
  Llamar 24 POST "/api/obras/internos/fases/$faseA/avances" 'sistema' '{"porcentaje":100}' 409 | Out-Null
  Llamar 25 POST "/api/obras/internos/fases/$faseB/avances" 'sistema' '{"porcentaje":40}' 201 { param($j,$x) $x -match '"estado":"EN_CURSO"' } | Out-Null
  Llamar 26 PATCH "/api/obras/internos/fases/$faseB/riesgo" 'capataz' $null 403 | Out-Null
  Llamar 27 PATCH "/api/obras/internos/fases/$faseB/riesgo" 'sistema' $null 200 { param($j,$x) $x -match '"estado":"EN_RIESGO"' } | Out-Null
  Llamar 28 POST "/api/obras/internos/fases/$faseB/avances" 'sistema' '{"porcentaje":50}' 409 | Out-Null
  Llamar 29 PATCH "/api/obras/fases/$faseB/programacion" 'capataz' $repro 403 | Out-Null
  Llamar 30 PATCH "/api/obras/fases/$faseB/programacion" 'planificador' $repro 200 { param($j,$x) $x -match '"estado":"PENDIENTE"' -and $x -match '"fechaInicioPlanificada":"2026-12-12"' -and $x -match '"fechaFinPlanificada":"2026-12-17"' } | Out-Null
  Llamar 31 GET "/api/obras/$obraId/cronograma" 'auditor' $null 200 { param($j,$x) $x -match 'COMPLETADA' -and $x -match 'PENDIENTE' } | Out-Null
  Llamar 32 GET "/api/obras/$obraId" 'capataz' $null 200 { param($j,$x) $x -match '"estado":"EN_EJECUCION","avance":70' } | Out-Null
  Llamar 33 GET '/api/obras' 'auditor' $null 200 { param($j,$x) $x -match '"avance"' } | Out-Null
  Llamar 34 GET "/api/obras/$obraId/tramos" 'planificador' $null 200 { param($j,$x) $x -match 'Tramo norte' } | Out-Null
  Llamar 35 GET "/api/obras/internos/fases/$faseB/contexto-ejecucion" 'operario' $null 403 | Out-Null
  Llamar 36 GET "/api/obras/internos/fases/$faseB/contexto-ejecucion" 'planificador' $null 403 | Out-Null
  Llamar 37 GET "/api/obras/internos/fases/$faseB/contexto-ejecucion" 'sistema' $null 200 { param($j,$x) $x -match '"faseAnteriorCompletada":true' } | Out-Null
  Llamar 38 GET '/v3/api-docs' 'sin' $null 200 | Out-Null

  ""
  "RESULTADO: $($script:ok) de $($script:total) OK"
}
