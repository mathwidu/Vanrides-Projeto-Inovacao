# QA MapLibre Device Smoke

Este contrato define como provar a superficie MapLibre em device/simulator antes
de promover o renderer no VanRides.

## Regra De Promocao

- MapLibre nao e default.
- Google Maps continua como renderer legado/fallback ate smoke nativo passar.
- O smoke atual usa style sintetico local; ele nao prova tile service, style
  endpoint, PMTiles/MBTiles, offline pack ou cache self-hosted real.
- Ausencia de iOS Simulator/device deve ser registrada como
  `BLOCKED_VALIDATION_DEVICE`, nao como sucesso.

## Comandos

Gate rapido sem device:

```bash
./scripts/qa_maplibre_device_smoke.sh --check-only
```

Smoke real:

```bash
./scripts/qa_maplibre_device_smoke.sh --run-device --device <ios-simulator-id>
```

Internamente, o modo real usa `flutter drive` com
`test_driver/maplibre_device_smoke_driver.dart`, porque nesta versao do Flutter
o comando `flutter test` nao aceita `--publish-port`.

Smoke real em iPhone iOS wireless quando o Flutter pedir publicacao de porta:

```bash
./scripts/qa_maplibre_device_smoke.sh --run-device --publish-port --device <ios-device-id>
```

Tambem e possivel habilitar o mesmo comportamento por ambiente:

```bash
VANRIDES_MAPLIBRE_PUBLISH_PORT=1 ./scripts/qa_maplibre_device_smoke.sh --run-device --device <ios-device-id>
```

Evidencias locais ficam fora do Git em:

```text
output/maplibre-device-smoke/
```

## Cenarios

| Cenario | Superficie | Prova | Evidencia |
| --- | --- | --- | --- |
| `tracking-maplibre-proof` | `TripLiveMapPanel` | Render MapLibre atras de flag | log e screenshot apos marcador visual-ready quando simulador permitir |
| `default-fallback` | `TripLiveMapRenderer` | Google continua default sem flag | widget/unit tests existentes |

## Flags Obrigatorias

O modo real deve passar:

- `--dart-define=VANRIDES_MAPLIBRE_PROOF=true`
- `--dart-define=VANRIDES_MAPLIBRE_DEVICE_SMOKE=true`

## Limites

- Nao roda em Codex Cloud/Linux.
- Nao valida trafego real, reroute, tile cache, offline pack ou cobertura BR-RS.
- Nao substitui o futuro smoke de style/tile self-hosted.
- `--publish-port` apenas remove o bloqueio de startup de iPhone iOS wireless;
  ele nao prova tile service, style real, PMTiles/MBTiles ou offline pack.

## Smoke Style/Tile Self-Hosted

O gate rapido tambem valida o contrato style/tile sem device:

```bash
./scripts/qa_maplibre_style_tile_smoke.sh --check-only
```

Saida esperada:

```text
MAPLIBRE_STYLE_TILE_SMOKE_OK source=vanrides-vector-tiles region=BR-RS tileSet=br-rs-smoke-v1
```

Este smoke exige que o style MapLibre declare `vanrides-vector-tiles`, metadata
da regiao `BR-RS`, `style-smoke-v1` e `br-rs-smoke-v1`. A URL de tile usada
nesta fatia e local/controlada para validar formato e politica; ela nao busca tiles reais,
nao exige VPS e nao prova PBF, PMTiles/MBTiles, offline pack ou cobertura BR-RS.

## Smoke Fixture/Fetch Local De Tile

O gate rapido tambem valida que a URL do style consegue buscar uma fixture local
controlada:

```bash
./scripts/qa_maplibre_tile_fixture_smoke.sh --check-only
```

Saida esperada:

```text
MAPLIBRE_TILE_FIXTURE_SMOKE_OK url=http://127.0.0.1:8099/vanrides/maps/BR-RS/vector/0/0/0.pbf bytes=23 sha256=5cdc92d5acdac6490fdd5f95099328b0d0c0126b63cb6b8faf147aae62ed615c
```

Este smoke sobe um servidor efemero em `127.0.0.1:8099`, serve uma fixture local
minima em `/vanrides/maps/BR-RS/vector/0/0/0.pbf` e busca o tile pelo mesmo
formato de URL declarado no style. Ele valida status HTTP, content-type, CORS,
bytes e hash da fixture local; ele nao prova geracao real de tiles, cobertura
BR-RS, PMTiles/MBTiles, offline pack, reroute, deploy ou VPS.
