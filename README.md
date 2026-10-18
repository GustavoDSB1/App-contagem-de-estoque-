# 📦 Contagem de Estoque (App Android)

Aplicativo Android simples e visual para contar estoque e gerar uma planilha Excel no final.

## O que o app faz

- **Cadastrar produtos** com nome e um ícone (🥤 🍞 🧴 ...) **ou uma foto** (galeria ou câmera; recortada
  em círculo automaticamente). Pode já informar a quantidade contada.
- **Somar quantidades**: toque no produto, digite quanto contou e salve. Na próxima vez o valor é
  **somado** ao que já existe (ex.: 12 + 5 = 17). Botões rápidos `+1`, `+5`, `+10`, `+50`.
- **Corrigir o total** caso tenha contado errado.
- **Acompanhar o progresso**: o topo mostra quantos produtos já foram contados e o total de itens.
  Cada produto mostra "✅ Contado" ou "⏳ Falta contar".
- **Gerar planilha Excel (.xlsx)** com todos os produtos, quantidades e a **data da contagem**:
  - Aba **Contagem**: produto, quantidade, data/hora da última contagem e total geral.
  - Aba **Histórico**: cada lançamento feito (data e hora, produto, somado/corrigido, quantidade).
  - O arquivo é salvo na pasta **Downloads** (Android 10+) e pode ser enviado por WhatsApp, e-mail, Drive...
- **Filtros** na lista: Todos, ⏳ Falta contar e 💲 Sem valor (para achar rápido os produtos sem preço).
  Ao abrir um produto e voltar, a lista continua no mesmo lugar.
- **Nova contagem**: zera as quantidades mantendo os produtos cadastrados.
- **Importar produtos de planilha Excel (.xlsx)**: use a planilha gerada pelo próprio app (por exemplo,
  antes de trocar de celular ou reinstalar) ou qualquer planilha com uma coluna **Produto**
  (opcionais: **Quantidade**, **Valor**, **Última contagem**). Mostra uma prévia, ignora nomes que já
  existem, escolhe um ícone pelo nome e pergunta se deve trazer as quantidades.
- **Valor de venda** opcional em cada produto (não vai para a planilha).
- **Aba Consumo** (baixa de consumo de funcionários e diaristas — **não altera o estoque nem a contagem**):
  1. Escolha **👷 Funcionário** ou **🧹 Diarista** e o dia (hoje, ontem...).
  2. Toque no nome de quem consumiu (funcionários e diaristas ficam cadastrados; **➕ Novo** cadastra na hora).
  3. Toque no produto na **grade** (use a 🔍 lupa para buscar pelo nome), ajuste a quantidade (− / +) e
     confirme **Dar baixa**.
     Na hora já aparece a mensagem dessa baixa com o botão **Enviar no WhatsApp**
     (e a opção de enviar todas as baixas do dia).
  4. O app mostra o total por pessoa e do dia, e monta a mensagem pronta para **Enviar no WhatsApp**
     (ou **Copiar**), no modelo:

     ```
     Consumo funcionário 02/10
     Ricardo 1 coca 600 9,00

     Leonardo 1 guaraná lata 7,00
     ```
- **Aba ⚙️ Mais**:
  - Cadastro de **funcionários** e **diaristas** (adicionar/remover).
  - **Backup para outro celular**: exporta um arquivo `.json` com produtos, preços, ícones/fotos,
    funcionários e diaristas (sem contagem e sem baixas). No outro celular: ⚙️ Mais → Importar backup.
    Produtos com o mesmo nome recebem o preço do backup; nada é apagado.
  - **Fora da planilha Excel**: lista de produtos que não vão para a planilha de contagem
    (também dá para marcar na tela do produto).
  - Importar produtos de planilha Excel.
- Tudo fica salvo no celular, funciona **sem internet**.

## Como instalar no celular

1. No GitHub, abra a aba **Actions** → workflow **"Gerar APK"**.
2. Abra a execução mais recente (com ✅) e baixe o artefato **ContagemEstoque-apk**.
3. Descompacte o `.zip` e envie o arquivo `ContagemEstoque-debug.apk` para o celular.
4. Abra o APK no celular e permita "instalar apps de fontes desconhecidas" quando o Android pedir.

## Como compilar no computador

Requisitos: Android Studio (ou JDK 17 + Android SDK 35).

```bash
./gradlew assembleDebug        # gera app/build/outputs/apk/debug/ContagemEstoque-debug.apk
./gradlew testDebugUnitTest    # testes da planilha, importação, backup e mensagem do WhatsApp
```

Ou abra a pasta do projeto no Android Studio e clique em **Run ▶**.

## Chave de assinatura (importante)

Todas as versões do APK precisam ser assinadas com **a mesma chave**; é isso que permite instalar uma
versão nova por cima da antiga sem perder os dados. Como este repositório é público, o arquivo da chave
**não** fica aqui: ele é guardado como *Secret* do GitHub.

- No GitHub: **Settings → Secrets and variables → Actions → New repository secret**
  - Nome: `KEYSTORE_BASE64`
  - Valor: o conteúdo (texto) do arquivo `estoque.keystore` convertido em Base64
- Para compilar no computador com a mesma chave, coloque o arquivo em `app/estoque.keystore`
  (ele é ignorado pelo Git). Sem o arquivo, o build usa a chave de debug padrão, que serve para
  testar mas não atualiza o app já instalado.
- Guarde uma cópia do `estoque.keystore` em lugar seguro: sem ele não é possível gerar atualizações
  que instalem por cima.

## Tecnologias

- Kotlin + Jetpack Compose (Material 3)
- Android 8.0 (API 26) ou superior
- Planilha `.xlsx` gerada pelo próprio app (sem bibliotecas externas), em `excel/XlsxWriter.kt`

## Estrutura

```
app/src/main/java/com/estoque/contagem/
├── MainActivity.kt              # Navegação entre telas, snackbar e exportação
├── consumo/
│   ├── ConsumptionMessage.kt    # Mensagem do WhatsApp, valores (R$) e datas
│   └── WhatsAppShare.kt         # Abre o WhatsApp com a mensagem
├── data/
│   ├── Product.kt               # Produto e lançamentos da contagem
│   ├── Consumption.kt           # Baixa de consumo (funcionário/diarista)
│   ├── Person.kt                # Funcionário/diarista cadastrado
│   ├── Backup.kt                # Formato do arquivo de backup
│   ├── ProductImages.kt         # Fotos dos produtos (recorte, tamanho, cache)
│   └── ProductRepository.kt     # Salva/carrega os dados (JSON no celular)
├── excel/
│   ├── XlsxWriter.kt            # Gera o arquivo .xlsx
│   ├── StockReport.kt           # Monta as abas "Contagem" e "Histórico"
│   ├── XlsxReader.kt            # Lê arquivos .xlsx (importação)
│   ├── ProductImport.kt         # Converte as linhas da planilha em produtos
│   └── ExcelExporter.kt         # Salva em Downloads, compartilha e abre
└── ui/
    ├── StockViewModel.kt        # Regras: somar, corrigir, excluir, nova contagem
    ├── Format.kt                # Datas, números e busca sem acento
    ├── theme/Theme.kt           # Cores do app
    ├── components/Components.kt # Ícone do produto, formulários e confirmações
    └── screens/                 # Estoque, produto, Consumo e "planilha pronta"
```
