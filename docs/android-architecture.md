# Android module architecture

The sender is a multi-module Gradle build. `:app` is the application and the only
composition root. `:core:coroutines` is a plain JVM library because it touches no
Android API; every other module is an Android library.

## Module graph

```
                          ┌──────────┐
                          │   :app   │  application, Hilt entry point
                          └────┬─────┘
             ┌─────────────────┼───────────────────────────────┐
             │                 │ (implementation of every impl)│
             ▼                 ▼                               ▼
   ┌──────────────────┐  ┌───────────────┐            ┌────────────────┐
   │ :feature:transfer│  │ :domain:*:impl│            │ :data:*:impl   │
   │  Compose + VM    │  └───────┬───────┘            └───────┬────────┘
   └────────┬─────────┘          │                            │
            │                    ▼                            ▼
            │            ┌───────────────┐            ┌────────────────┐
            └───────────►│ :domain:*:api │            │ :data:*:api    │
                         └───────┬───────┘            └───────┬────────┘
                                 │                            │
                                 └──────────┬─────────────────┘
                                            ▼
                                  ┌──────────────────────┐
                                  │ :core:model          │
                                  │ :core:coroutines     │
                                  └──────────────────────┘
```

`:domain:*:impl` depends on `:data:*:api`, never on `:data:*:impl`.

## Modules

| Module | Owns |
|---|---|
| `:app` | `PhotoTransferApplication`, `MainActivity`, the dependency graph |
| `:feature:transfer` | `TransferScreen`, `TransferViewModel` |
| `:domain:transfer:api` | `TransferCoordinator`, `TransferState` |
| `:domain:transfer:impl` | `DefaultTransferCoordinator`: session lifecycle, progress, failure states |
| `:domain:discovery:api` | `ObserveReceivers` |
| `:domain:discovery:impl` | Folds discovery events into the current receiver list |
| `:domain:media:api` | `ResolveSelectedPhotos` |
| `:domain:media:impl` | Dedupes the picker result and resolves metadata off the main thread |
| `:data:transfer:api` | `TransferGateway`, `TransferHandle`, `PendingUpload`, `ReceiverInfo` |
| `:data:transfer:impl` | `HttpTransferGateway`, OkHttp request bodies, wire DTOs |
| `:data:discovery:api` | `ReceiverDiscoveryDataSource`, `DiscoveryEvent` |
| `:data:discovery:impl` | `NsdManager` discovery and serialized service resolution |
| `:data:media:api` | `MediaMetadataSource`, `MediaByteSource` |
| `:data:media:impl` | `ContentResolver`-backed metadata and byte access |
| `:core:model` | `ReceiverDevice`, `SelectedFile` |
| `:core:coroutines` | `Dispatchers`, `@ApplicationScope` |

## Rules

1. **Only `api` modules are depended on.** The single exception is `:app`, which
   adds every `impl` so the runtime graph is complete.
2. **`api` modules hold contracts only:** interfaces, sealed states, value types.
   No Hilt, no frameworks, no SDK types in public signatures.
3. **Hilt lives in `integration` packages.** `@Module`, `@Binds`, and `@Provides`
   appear only in `.../integration/`, which for every layered module means
   `<module>/impl/.../integration/`. Consumers use constructor injection and
   never see a Hilt module. `@HiltViewModel` in `:feature:transfer` is a
   consumer annotation, not wiring.
4. **Implementations are `internal`** and live in `.../impl/.../internal/`, so a
   binding is the only way to reach them.
5. **Wire and platform types do not cross boundaries.** The HTTP manifest DTOs
   are `internal` to `:data:transfer:impl`; `TransferGateway` speaks in
   `SelectedFile` and `ReceiverDevice`.
6. **Features never depend on other features** or on any `impl`.

## Why `data` also splits into `api`/`impl`

Domain code depends on `:data:*:api`, so the domain layer compiles without
OkHttp, `NsdManager`, or `ContentResolver` anywhere on its classpath. Swapping
plain HTTP for TLS, or `NsdManager` for another discovery mechanism, is a new
`impl` and one changed line in `:app`.

## Coroutines

`:core:coroutines` owns two contracts:

- **`Dispatchers`**, a value type holding `main`, `io`, and `default`. Classes
  inject it rather than referencing `kotlinx.coroutines.Dispatchers`, so a test
  passes `Dispatchers(main = testDispatcher, io = testDispatcher, ...)` and needs
  no dispatcher rule.
- **`@ApplicationScope`**, qualifying a singleton `CoroutineScope` backed by a
  `SupervisorJob`. Work that must outlive the screen that started it takes this
  scope: `DefaultTransferCoordinator` uses it so a transfer keeps running across
  configuration changes and navigation.

## Build logic

Shared Gradle configuration lives in `android/build-logic` as convention plugins:

| Plugin | Applies |
|---|---|
| `phototransfer.android.application` | AGP application, SDK levels, Java/Kotlin 17 |
| `phototransfer.android.library` | AGP library, SDK levels, Java/Kotlin 17, unit test deps |
| `phototransfer.android.compose` | Compose compiler, Compose BOM |
| `phototransfer.android.hilt` | KSP, Hilt Android plugin, `hilt-android` and its compiler |
| `phototransfer.jvm.library` | Kotlin JVM, Java/Kotlin 17, unit test deps |
| `phototransfer.jvm.hilt` | KSP, `hilt-core` and `hilt-compiler` |

AGP 9 provides Kotlin support directly, so no Kotlin Android plugin is applied.
JVM modules only declare bindings, so they skip the Hilt Android plugin (which
exists for the `@AndroidEntryPoint` bytecode transform) and use `hilt-core`.

## Tests

Each `impl` module tests its own behavior against fakes of the `api` it consumes:

| Module | Test |
|---|---|
| `:data:media:impl` | `ContentResolverMediaSourceTest`: metadata fallbacks, stream errors |
| `:data:transfer:impl` | `HttpTransferGatewayTest`: the wire protocol against `MockWebServer` |
| `:domain:discovery:impl` | `DefaultObserveReceiversTest`: found/lost aggregation |
| `:domain:media:impl` | `DefaultResolveSelectedPhotosTest`: dedupe and ordering |
| `:domain:transfer:impl` | `DefaultTransferCoordinatorTest`: state machine, cancel and replace |
