# The Manager

Gerenciador de arquivos Android avançado, open source e com implementação própria.

O projeto busca paridade funcional com gerenciadores técnicos de duas janelas sem copiar código, marca, textos, ícones ou recursos proprietários de outros aplicativos.

## Estado atual

- Android 11+ (`minSdk 30`), Kotlin e Jetpack Compose.
- Navegação real em duas janelas, seleção múltipla e histórico por painel.
- Modos de acesso Normal, Root, Shizuku/Sui e Depuração sem fio (ADB TLS).
- Operações reais: criar pasta/arquivo, copiar, mover, renomear e excluir.
- Visualizador/editor de texto com gravação atômica e visualizador hexadecimal.
- Hash SHA-256 e propriedades de arquivos.
- Proteções contra exclusão de raízes críticas e injeção em comandos internos.
- CI no GitHub Actions com lint, testes e APK debug.

Consulte [docs/ROADMAP.md](docs/ROADMAP.md) para o mapa de paridade e o que ainda será implementado.

## Compilar

Requisitos: JDK 17, Android SDK 35 e Gradle 8.9.

```bash
gradle :app:assembleDebug
```

O workflow `Android CI` também publica `TheManager-debug.apk` como artifact.

## Segurança

Os modos Root, Shizuku e ADB são opt-in. O app não executa ações privilegiadas sem escolha explícita do usuário. A chave ADB é criada no Android Keystore e não é exportável. Operações destrutivas bloqueiam raízes críticas e exigem confirmação na interface.

## Licença

Apache-2.0. Dependências de terceiros mantêm suas próprias licenças.
