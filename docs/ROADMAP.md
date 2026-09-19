# Mapa de paridade funcional

Este projeto é uma implementação independente. “Paridade” significa oferecer capacidades equivalentes, não copiar a interface, código, marca ou recursos de outro aplicativo.

## Implementado no núcleo atual

| Área | Estado | Observação |
|---|---:|---|
| Gerenciador de duas janelas | Funcional | Duas pastas simultâneas em telas largas; alternância rápida no celular |
| Histórico independente | Funcional | Voltar, avançar, subir e abrir caminho absoluto por painel |
| Seleção múltipla | Funcional | Copiar, mover e excluir em lote |
| Operações de arquivo | Funcional | Criar, copiar, mover, renomear, excluir e salvar atomicamente |
| Modo Normal | Funcional | Pasta do app sem permissão; armazenamento compartilhado com permissão “todos os arquivos” |
| Root | Funcional | Detecção e execução por `su`; caminhos passados ao shell são escapados |
| Shizuku / Sui | Funcional | UserService Binder; operações são executadas com UID shell/root |
| ADB sem fio | Funcional | Pareamento TLS, conexão manual/descoberta e identidade no Android Keystore |
| Editor de texto | Funcional | UTF-8, detecção básica de binário e gravação atômica |
| Visualizador hexadecimal | Funcional | Offset, hexadecimal e ASCII, limitado a 2 MB nesta tela |
| Propriedades e hash | Funcional | Tamanho, data, permissões, acesso e SHA-256 |
| Layout adaptativo | Funcional | Celular, tablet e paisagem |

## Próximos blocos

1. Fila persistente de operações, progresso por bytes, pausa, retomada, retry, journal e lixeira/undo.
2. SAF completo como filesystem virtual, favoritos, abas, bookmarks e pesquisa indexada.
3. ZIP/APK/JAR/AAR/APKS/XAPK/7Z/TAR/GZIP/BZIP2/XZ/ZSTD com edição virtual e extração protegida.
4. Editor de código com busca/substituição, destaque, encoding, finais de linha e arquivos grandes.
5. Editor hexadecimal gravável com busca de bytes, bookmarks e inspector endian.
6. APK inspector, AXML, resources.arsc, assinatura, splits, DEX/Smali, XREF e rebuild.
7. App Manager, SQLite, imagem/9-patch, mídia, terminal PTY, SFTP/SMB/WebDAV e plugins.

Cada bloco deve entrar com testes reais. Recursos ainda não implementados não aparecem como botões falsos.
