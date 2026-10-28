# Política de privacidade do Posato

Em vigor desde 22 de outubro de 2026.

O Posato não envia ao desenvolvedor seus conjuntos de pausa, sua lista de
sites, suas escolhas de apps, sessões, agendamentos nem dados do espaço de
trabalho. Não há conta Posato, análises, publicidade nem servidor operado pelo
Posato. Esta política abrange o Posato para Mac, iPhone, Linux e Android. A
sincronização opcional pelo iCloud ou por uma pasta, o bloqueio em cada
dispositivo e as solicitações de atualização no macOS estão descritos abaixo.

## Dados que o Posato guarda nos seus dispositivos

Para funcionar, o Posato armazena em cada dispositivo:

- seus conjuntos de pausa: os nomes, qual conjunto é o padrão, quais domínios
  de sites cada um contém e quais apps você escolheu para cada um naquele
  dispositivo, armazenados no iPhone como seleções fornecidas pelo sistema que
  o Posato não consegue ler como nomes de apps; no Mac, no Linux e no Android,
  o Posato armazena quais apps são;
- suas sessões: quando começam e terminam, qual conjunto de pausa cada uma
  usou e se foram encerradas antes da hora;
- seus agendamentos: os nomes, os conjuntos de pausa, os dias da semana e os
  horários em que se repetem, se cada um está ativado e quais pausas agendadas
  você pulou ou encerrou antes da hora;
- ajustes como se a sincronização está ativada, qual pasta você escolheu para
  a sincronização por pasta e se o auxiliar do macOS está ativado;
- com a sincronização ativada, quando este dispositivo concluiu a última
  sincronização.

Esses dados ficam no armazenamento privado do Posato no dispositivo até você
alterá-los ou removê-los. No Mac, no iPhone e no Linux, esses dados, exceto as
escolhas de apps no iPhone, também podem ser incluídos nos backups do
dispositivo, como o Backup do iCloud, o Time Machine ou um backup da sua pasta
pessoal no Linux, conforme seus ajustes de backup. No Android, o Posato exclui
seus dados dos backups e das transferências de dispositivo do Android. No Mac,
mover o Posato para o Lixo não apaga esses dados; eles permanecem na pasta de
dados do aplicativo do Posato até você removê-los. No Linux, remover o pacote
também não os apaga; eles permanecem na pasta do Posato dentro dos dados de
aplicativos da sua pasta pessoal até você removê-los.

Com a sincronização ativada, pelo iCloud ou por uma pasta, o Posato também
guarda o histórico de alterações sincronizadas no armazenamento privado de cada
dispositivo conectado: cada conjunto de pausa criado, renomeado ou apagado,
cada site adicionado a um conjunto ou removido dele, qual conjunto é o padrão e
qual conjunto cada sessão e cada agendamento usou, e quando cada alteração foi
feita, inclusive quando as sessões anteriores começaram, quando estavam
programadas para terminar e se foram encerradas antes da hora. Os registros de
antes do Posato 1.3 também guardam as alterações do nome do grupo de apps
compartilhado que ele usava na época. Alterar ou remover um site ou um conjunto
não apaga os registros anteriores dele desse histórico. O histórico fica até
**Remover espaço de trabalho** ser concluído naquele dispositivo ou até os
dados do Posato serem removidos dele, e pode ser incluído nos backups do
dispositivo como os dados acima.

O Posato não coleta histórico de navegação, as páginas que você visita, quais
páginas foram bloqueadas, com que frequência você abre apps, pontuações de uso
nem qualquer outro registro de atividade.

## Sincronização pelo iCloud

A sincronização pelo iCloud é opcional e fica desativada até você escolher
**Sincronizar com o iCloud**.

- Seus conjuntos de pausa (os nomes, os sites e qual é o padrão), nas
  alterações de antes do Posato 1.3 o nome do grupo de apps compartilhado, o
  início e o fim das sessões e seus agendamentos, inclusive as pausas
  agendadas puladas e encerradas, são criptografados no seu dispositivo antes
  de serem armazenados no banco de dados privado do CloudKit da sua própria
  conta do iCloud. O desenvolvedor do Posato não pode lê-los nem acessá-los.
- Cada alteração, e o registro de cada dispositivo, é armazenado como um
  registro criptografado separado, e esses registros se acumulam no seu banco
  de dados do iCloud até você escolher **Remover espaço de trabalho**; o
  Posato não os limpa automaticamente.
- A chave de criptografia é compartilhada entre seus dispositivos pelas Chaves
  do iCloud.
- As escolhas de apps nunca são sincronizadas; cada dispositivo guarda as
  suas.
- A Apple opera o iCloud e pode observar informações técnicas necessárias para
  oferecê-lo, como a associação à sua conta, horários, tamanhos e quantidades
  de registros, conforme a política de privacidade da própria Apple.

**Remover espaço de trabalho** apaga os registros sincronizados do Posato do
seu banco de dados do iCloud, o histórico de alterações sincronizadas no
dispositivo em que você o remove e a chave do espaço de trabalho das Chaves do
iCloud, então seus outros dispositivos também perdem o acesso ao espaço de
trabalho; as cópias próprias deles dos seus conjuntos de pausa, sessões e
histórico de alterações ficam nesses dispositivos até você remover o espaço de
trabalho neles. Os conjuntos de pausa e sites salvos no dispositivo em que você
remove o espaço de trabalho continuam nesse dispositivo. A Apple pode manter
backups por um período, conforme as próprias políticas. Se todas as cópias da
chave forem perdidas, os dados sincronizados não podem ser recuperados.

## Sincronização por pasta

A sincronização por pasta é opcional e fica desativada até você escolher uma
pasta no Posato. Ela é a única sincronização no Linux e no Android e, no Mac e
no iPhone, substitui a sincronização pelo iCloud naquele espaço de trabalho.

- O Posato grava uma pasta `Posato` dentro da pasta que você escolher. Ela
  contém um arquivo com identificadores aleatórios do espaço de trabalho e,
  para cada alteração, um arquivo separado criptografado no seu dispositivo com
  a mesma criptografia da sincronização pelo iCloud: seus conjuntos de pausa
  (os nomes, os sites e qual é o padrão), o início e o fim das sessões e seus
  agendamentos, inclusive as pausas agendadas puladas e encerradas. As
  escolhas de apps nunca são sincronizadas; cada dispositivo guarda as suas.
- Esses arquivos se acumulam na pasta até você escolher **Remover espaço de
  trabalho**, que apaga dela a pasta do Posato. O Posato não os limpa
  automaticamente.
- O Posato não envia nada por conta própria. O serviço que mantém a pasta
  sincronizada, como Dropbox, OneDrive, iCloud Drive ou Syncthing, copia os
  arquivos conforme a própria política de privacidade. Ele não consegue ler o
  conteúdo deles, mas pode observar informações técnicas como os nomes dos
  arquivos, que são aleatórios, os tamanhos, as quantidades e quando os
  arquivos mudam, e pode manter versões anteriores ou arquivos apagados por um
  tempo.
- Na sincronização por pasta, cada dispositivo guarda a chave do espaço de
  trabalho em um arquivo no armazenamento privado do Posato que só a sua conta
  de usuário pode ler, e não nas Chaves do iCloud. No Android, o arquivo também
  é protegido com uma chave que nunca sai do Android Keystore daquele celular.
  No Mac, no iPhone e no Linux, o arquivo da chave pode ser incluído nos
  backups do dispositivo como os dados acima.
- Para adicionar um dispositivo, o Posato em um dispositivo do espaço de
  trabalho mostra um código de pareamento de uso único, também como código QR
  e como um link `posato://` que contém apenas esse código. Ele coloca a chave
  do espaço de trabalho na pasta, criptografada com uma chave derivada do
  código. Ele apaga esse arquivo quando o novo dispositivo entra, quando você
  fecha o código ou, depois que o código expira após 10 minutos, na próxima
  vez que o Posato iniciar ou mostrar um novo código. O Posato mostra o código
  QR, mas não usa sua câmera. Se você compartilhar o link por mensagem, e-mail
  ou área de transferência, ele passa pelo app que você escolher. Qualquer
  pessoa que tenha o código ou o link e consiga abrir a pasta, ou uma cópia
  desse arquivo no histórico de arquivos dela, pode recuperar a chave do
  espaço de trabalho, mesmo depois que o código expira, então compartilhe-os
  apenas com seus próprios dispositivos.

**Remover espaço de trabalho** apaga a pasta do Posato da pasta que você
escolheu, se este dispositivo ainda conseguir acessá-la, e também o arquivo da
chave do espaço de trabalho e o histórico de alterações sincronizadas no
dispositivo em que você o remove. Os outros dispositivos mantêm as cópias
próprias dos seus conjuntos de pausa, sessões, histórico de alterações e
arquivo da chave até você remover o espaço de trabalho neles. O serviço de
sincronização pode manter versões anteriores dos arquivos conforme a própria
política. Se todas as cópias da chave forem perdidas, os dados sincronizados
não podem ser recuperados.

## Tempo de Uso no iPhone

No iPhone, o Posato usa o framework Tempo de Uso da Apple para bloquear os
sites e apps que você escolher. Suas escolhas de apps são seleções opacas do
sistema que ficam no dispositivo; o Posato não recebe nomes de apps nem dados de
uso do Tempo de Uso. Para que um agendamento possa começar com o Posato
fechado, o Posato guarda uma cópia dos seus agendamentos e dos sites e
escolhas de apps de cada conjunto de pausa em um armazenamento compartilhado
apenas com a própria extensão do Tempo de Uso no mesmo iPhone. Essa cópia nunca
sai do dispositivo, é excluída dos backups do dispositivo e é apagada quando
os dados do Posato ou o espaço de trabalho são removidos.

## Bloqueio no Mac

No Mac, o auxiliar em segundo plano do Posato bloqueia os sites escolhidos
direcionando o tráfego do navegador por um proxy local no seu Mac enquanto uma
sessão está ativa. O auxiliar examina cada solicitação apenas pelo tempo
necessário para permiti-la ou bloqueá-la e não a armazena nem a envia. Para
mostrar a página de pausa na aba atual do Safari ou do Chrome, o Posato pode
usar a permissão de Automação; ele lê apenas o endereço da aba atual naquele
momento e não guarda nenhum registro. O Posato não descriptografa o tráfego
HTTPS.

Se você escolher iniciar sessões em um Mac sem a senha de administrador, o
auxiliar guarda um registro dessa permissão em um arquivo do sistema que só o
sistema pode ler. O registro contém o número de usuário e o identificador de
sistema da sua conta do Mac e o identificador de hardware do seu Mac. Ele nunca
sai do seu Mac e é apagado quando você desativa a opção ou remove o Posato
deste Mac.

## Bloqueio no Linux

No Linux, o Posato instala um serviço de pausa que roda com permissões do
sistema, uma única vez, com sua senha. Só a sua conta de usuário e o sistema
podem enviar solicitações a ele. Durante uma pausa, o serviço:

- adiciona cada site pausado e o nome dele com `www` ao arquivo do sistema
  `/etc/hosts`, que outras contas de usuário do computador podem ler, e os
  remove quando a pausa termina;
- encerra os programas pausados que você escolheu e que rodam na sua conta de
  usuário, verificando qual programa é cada processo em execução, sem ler nem
  guardar o que você faz nele.

O serviço guarda seu nome de usuário e seu número de usuário em um arquivo de
configuração do sistema, e a pausa que ele aplica, com os sites e os locais dos
programas, em um arquivo do sistema que só o sistema pode ler, para que uma
reinicialização mantenha a pausa. Depois que a pausa termina, esse arquivo
guarda apenas o identificador da sessão até o Posato lê-lo. Remover o pacote
remove o serviço, restaura o `/etc/hosts` e apaga os dois arquivos. Nada do que
o serviço processa sai do seu computador.

## Bloqueio no Android

No Android, o Posato precisa de várias permissões, cada uma concedida nas
próprias configurações do Android:

- **VPN.** Durante uma pausa com sites, o Posato roda uma VPN local que
  transporta apenas consultas de nomes, as perguntas que o celular faz para
  encontrar o endereço de um site. Ele mesmo responde aos nomes pausados e
  repassa todas as outras consultas ao servidor DNS da rede em que você está,
  ou ao DNS público do Google quando essa rede não oferece nenhum que o Posato
  possa usar. Nenhum outro tráfego entra na VPN, e o Posato não registra nem
  guarda as consultas.
- **Acesso ao uso.** Durante uma pausa com apps, o Posato verifica cerca de
  duas vezes por segundo qual app está em primeiro plano, para poder cobrir um
  app pausado com a tela de bloqueio. Ele guarda apenas o app em primeiro plano
  naquele momento, na memória, e não registra o uso de apps.
- **Sobreposição a outros apps**, para mostrar a tela de bloqueio.
- **Notificações, alarmes exatos e execução sem limites de bateria**, para que
  as pausas e os agendamentos comecem e terminem na hora com o Posato fechado.
  Uma notificação silenciosa aparece enquanto o Posato os mantém em execução.
- **Acesso a todos os arquivos**, para que o Posato possa ler e gravar na
  pasta que você escolher para a sincronização por pasta. O Posato lê e grava
  apenas nessa pasta.

O Posato lê a lista de apps que você pode abrir pelo inicializador, para que
você possa escolher os apps a pausar; a lista fica no celular. A pausa que ele
aplica, com os sites e os nomes de pacote dos apps, fica no armazenamento
privado do Posato até a pausa terminar, para que uma reinicialização a mantenha.

## Exportação e importação

Você pode exportar seus conjuntos de pausa para um arquivo e importá-lo em
outro dispositivo. O arquivo contém os nomes e os sites dos seus conjuntos de
pausa, qual conjunto é o padrão e seus agendamentos. Ele nunca contém escolhas
de apps, sessões, histórico, nomes de dispositivos nem chaves de sincronização.
O Posato grava o arquivo apenas onde você o salvar ou compartilhar e não envia
nada a lugar nenhum por conta própria.

- Sem um código, qualquer pessoa que receber o arquivo pode ler seus conjuntos
  de pausa e sites.
- Com **Proteger com um código**, o arquivo é criptografado com uma chave
  derivada de um código de nove caracteres que o Posato mostra uma única vez e
  não guarda. O código impede a leitura casual de um arquivo que chegue à
  pessoa errada, mas alguém que tenha o arquivo e gaste muito poder
  computacional tentando adivinhar poderia abri-lo, então envie o arquivo e o
  código apenas para onde você confia.
- No iPhone, um arquivo compartilhado é uma cópia temporária que o Posato apaga
  quando a planilha de compartilhamento fecha, e um arquivo importado é lido de
  uma cópia temporária que o Posato apaga depois de lê-la.

Uma importação altera seus conjuntos de pausa e agendamentos como edições
feitas à mão; se a sincronização estiver ativada, as alterações chegam aos seus
outros dispositivos.

## Notificações

No Mac e no iPhone, o Posato pode mostrar avisos locais quando uma pausa
termina ou começa em outro dispositivo. Eles são criados no seu dispositivo
pelo sistema de notificações dele; o Posato não envia nada a um servidor para
isso. Você pode desativá-los em Sobre o Posato ou nos ajustes do dispositivo.

## Atualizações no Mac

O Posato para iPhone, Linux e Android não busca atualizações por conta
própria. No Mac, o Posato pergunta antes de buscar atualizações
automaticamente. Se você concordar, ele busca uma vez por dia enquanto o app
está em execução. Você pode desativar as buscas automáticas ou escolher Buscar
Atualizações você mesmo. Cada atualização exige uma ação sua para ser baixada
e instalada, e a instalação espera até que nenhuma sessão esteja ativa.

As informações de atualização e os downloads vêm do GitHub Releases por meio
do Sparkle, uma biblioteca de atualização incluída no Posato. As solicitações
não contêm sua lista de sites, escolhas de apps, sessões, dados do iCloud, um
identificador de instalação nem um perfil do sistema. O Posato compara versões
e requisitos do sistema no seu Mac.

O GitHub e seus provedores de entrega podem receber seu endereço IP, o horário
da solicitação, o recurso solicitado e informações técnicas da conexão. Um
endereço de download identifica a versão que você solicita. Eles tratam essas
informações conforme a
[declaração de privacidade do GitHub](https://docs.github.com/en/site-policy/privacy-policies/github-general-privacy-statement).
O GitHub disponibiliza contagens agregadas de downloads; o Posato não adiciona
análises de uso nem identificador de rastreamento. Abrir um link de versão ou
de suporte no navegador está sujeito às políticas desse site e aos ajustes do
seu navegador.

As preferências de atualização ficam armazenadas neste Mac e não são
sincronizadas pelo iCloud. O Sparkle armazena localmente o estado das buscas
de atualização e os arquivos temporários de atualização.

## Diagnóstico

O Posato não coleta nem envia diagnósticos, relatórios de falhas ou
telemetria. Além da sincronização que você escolher, das consultas que a VPN
do Android repassa e das solicitações de atualização descritas em Atualizações
no Mac, o Posato não faz nenhuma solicitação de rede por conta própria.
Seu sistema operacional ou sua loja de apps podem coletar diagnósticos
conforme os ajustes do dispositivo e as próprias políticas; o Posato não os
acessa nem adiciona nada a eles.

Se você pedir ajuda, compartilhe apenas o que quiser. Não inclua nomes de
sites, nomes de apps, detalhes do dispositivo, capturas de tela nem registros
em relatos públicos.

## Crianças

O Posato não foi feito para gerenciar o dispositivo de outra pessoa e não
coleta dados de ninguém intencionalmente.

## Alterações

As alterações desta política serão publicadas no mesmo endereço, com uma nova
data de entrada em vigor.

## Contato

Dúvidas sobre privacidade: privacy@posato.app
