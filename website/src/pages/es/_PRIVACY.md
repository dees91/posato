# Política de privacidad de Posato

En vigor desde el 22 de octubre de 2026.

Posato no envía a su desarrollador tus conjuntos de pausa, tu lista de sitios
web, las apps que eliges, tus sesiones, tus horarios ni los datos de tu espacio
de trabajo. No hay cuenta de Posato, ni analítica, ni publicidad, ni servidores
gestionados por Posato. Esta política se aplica a Posato para Mac, iPhone,
Linux y Android. La sincronización opcional con iCloud o a través de una
carpeta, el bloqueo en cada dispositivo y las solicitudes de actualización de
macOS se describen más abajo.

## Datos que Posato guarda en tus dispositivos

Para funcionar, Posato guarda en cada dispositivo:

- tus conjuntos de pausa: sus nombres, cuál es el predeterminado, qué dominios
  de sitios web contiene cada uno y qué apps elegiste para cada uno en ese
  dispositivo; en el iPhone se guardan como selecciones proporcionadas por el
  sistema que Posato no puede leer como nombres de apps, y en el Mac, Linux y
  Android Posato guarda de qué apps se trata;
- tus sesiones: cuándo empiezan y terminan, qué conjunto de pausa usó cada una
  y si terminaron antes de tiempo;
- tus horarios: sus nombres, sus conjuntos de pausa, los días de la semana y
  las horas en que se repiten, si cada uno está activado y qué pausas
  programadas omitiste o terminaste antes de tiempo;
- ajustes como si la sincronización está activada, qué carpeta elegiste para
  la sincronización por carpeta y si el asistente de macOS está activado;
- cuando la sincronización está activada, cuándo terminó este dispositivo de
  sincronizarse por última vez.

Estos datos permanecen en el almacenamiento privado de Posato en el
dispositivo hasta que los cambies o elimines. En el Mac, el iPhone y Linux,
estos datos, salvo las apps elegidas en el iPhone, también pueden incluirse en
las copias de seguridad del dispositivo, como la copia de seguridad de iCloud,
Time Machine o una copia de tu carpeta de inicio de Linux, según tus ajustes de
copia de seguridad. En Android, Posato excluye sus datos de las copias de
seguridad y de las transferencias entre dispositivos de Android. En el Mac,
mover Posato a la Papelera no elimina estos datos; permanecen en la carpeta de
datos de la aplicación de Posato hasta que los elimines. En Linux, desinstalar
el paquete tampoco los elimina; permanecen en la carpeta de Posato dentro de
los datos de aplicaciones de tu carpeta de inicio hasta que los elimines.

Cuando la sincronización está activada, a través de iCloud o de una carpeta,
Posato también guarda el historial de cambios sincronizados en su
almacenamiento privado en cada dispositivo vinculado: cada conjunto de pausa
creado, renombrado o eliminado, cada sitio web añadido a un conjunto o
eliminado de él, qué conjunto es el predeterminado y qué conjunto usó cada
sesión y cada horario, y cuándo se hizo cada cambio, incluido cuándo empezaron
las sesiones pasadas, cuándo estaba previsto que terminaran y si se terminaron
antes de tiempo. Las entradas anteriores a Posato 1.3 también registran los
cambios en el nombre del grupo de apps compartido que se usaba entonces.
Cambiar o eliminar un sitio web o un conjunto no borra sus entradas anteriores
de este historial. El historial se conserva hasta que **Eliminar espacio de
trabajo** se completa en ese dispositivo o se eliminan de él los datos de
Posato, y puede incluirse en las copias de seguridad del dispositivo como los
datos anteriores.

Posato no recopila el historial de navegación, las páginas que visitas, qué
páginas se bloquearon, con qué frecuencia abres apps, puntuaciones de uso ni
ningún otro registro de actividad.

## Sincronización con iCloud

La sincronización con iCloud es opcional y está desactivada hasta que eliges
**Sincronizar con iCloud**.

- Tus conjuntos de pausa (sus nombres, sus sitios web y cuál es el
  predeterminado), en los cambios anteriores a Posato 1.3 el nombre del grupo
  de apps compartido, el inicio y el fin de las sesiones, y tus horarios,
  incluidas las pausas programadas omitidas y terminadas, se cifran en tu
  dispositivo antes de guardarse en la base de datos privada de CloudKit de tu
  propia cuenta de iCloud. El desarrollador de Posato no puede leerlos ni
  acceder a ellos.
- Cada cambio, y el registro de cada dispositivo, se guarda como un registro
  cifrado independiente, y estos registros se acumulan en tu base de datos de
  iCloud hasta que eliges **Eliminar espacio de trabajo**; Posato no los
  limpia automáticamente.
- La clave de cifrado se comparte entre tus dispositivos a través del Llavero
  de iCloud.
- Las apps elegidas nunca se sincronizan; cada dispositivo conserva las suyas.
- Apple gestiona iCloud y puede observar la información técnica necesaria
  para prestar el servicio, como la asociación con tu cuenta, los momentos de
  acceso, los tamaños y el número de registros, conforme a la política de
  privacidad de Apple.

**Eliminar espacio de trabajo** elimina los registros sincronizados de Posato
de tu base de datos de iCloud, su historial de cambios sincronizados en el
dispositivo donde lo eliminas y la clave del espacio de trabajo del Llavero de
iCloud, de modo que tus otros dispositivos también pierden el acceso al espacio
de trabajo; sus propias copias de tus conjuntos de pausa, sesiones e historial
de cambios permanecen en esos dispositivos hasta que elimines allí el espacio
de trabajo. Los conjuntos de pausa y los sitios web guardados en el
dispositivo donde eliminas el espacio de trabajo permanecen en ese
dispositivo. Apple puede conservar copias de seguridad durante un tiempo
conforme a sus propias políticas. Si se pierden todas las copias de la clave,
los datos sincronizados no se pueden recuperar.

## Sincronización por carpeta

La sincronización por carpeta es opcional y está desactivada hasta que eliges
una carpeta en Posato. Es la única sincronización en Linux y Android, y en el
Mac y el iPhone sustituye a la sincronización con iCloud para ese espacio de
trabajo.

- Posato escribe una carpeta `Posato` dentro de la carpeta que elijas.
  Contiene un archivo con identificadores aleatorios del espacio de trabajo y,
  por cada cambio, un archivo independiente cifrado en tu dispositivo con el
  mismo cifrado que la sincronización con iCloud: tus conjuntos de pausa (sus
  nombres, sus sitios web y cuál es el predeterminado), el inicio y el fin de
  las sesiones, y tus horarios, incluidas las pausas programadas omitidas y
  terminadas. Las apps elegidas nunca se sincronizan; cada dispositivo
  conserva las suyas.
- Estos archivos se acumulan en la carpeta hasta que eliges **Eliminar espacio
  de trabajo**, que elimina de ella la carpeta de Posato. Posato no los limpia
  automáticamente.
- Posato no sube nada por sí mismo. El servicio que mantiene sincronizada la
  carpeta, como Dropbox, OneDrive, iCloud Drive o Syncthing, copia los
  archivos conforme a su propia política de privacidad. No puede leer su
  contenido, pero puede observar información técnica como los nombres de los
  archivos, que son aleatorios, sus tamaños, su número y cuándo cambian, y
  puede conservar durante un tiempo versiones anteriores o archivos
  eliminados.
- Con la sincronización por carpeta, cada dispositivo guarda la clave del
  espacio de trabajo en un archivo del almacenamiento privado de Posato que
  solo tu cuenta de usuario puede leer, no en el Llavero de iCloud. En
  Android, el archivo también se sella con una clave que nunca sale del
  Android Keystore de ese teléfono. En el Mac, el iPhone y Linux, el archivo
  de la clave puede incluirse en las copias de seguridad del dispositivo como
  los datos anteriores.
- Para añadir un dispositivo, Posato muestra en un dispositivo del espacio de
  trabajo un código de vinculación de un solo uso, también como código QR y
  como enlace `posato://` que solo contiene ese código. Coloca la clave del
  espacio de trabajo en la carpeta, cifrada con una clave derivada del código.
  Elimina ese archivo cuando el nuevo dispositivo se ha unido, cuando cierras
  el código o, una vez que el código ha caducado a los 10 minutos, la próxima
  vez que Posato se inicia o muestra un código nuevo. Posato muestra el código
  QR, pero no usa tu cámara. Si compartes el enlace por mensaje, por correo o
  mediante el portapapeles, pasa por la app que elijas. Cualquiera que tenga
  el código o el enlace y pueda abrir la carpeta, o una copia de ese archivo
  en su historial de archivos, puede recuperar la clave del espacio de
  trabajo, incluso después de que caduque el código, así que compártelos solo
  con tus propios dispositivos.

**Eliminar espacio de trabajo** elimina la carpeta de Posato de la carpeta que
elegiste, si este dispositivo aún puede acceder a ella, y el archivo de la
clave del espacio de trabajo y el historial de cambios sincronizados en el
dispositivo donde lo eliminas. Los demás dispositivos conservan sus propias
copias de tus conjuntos de pausa, sesiones, historial de cambios y archivo de
la clave hasta que elimines allí el espacio de trabajo. El servicio de
sincronización puede conservar versiones anteriores de los archivos conforme a
su propia política. Si se pierden todas las copias de la clave, los datos
sincronizados no se pueden recuperar.

## Tiempo de uso en el iPhone

En el iPhone, Posato usa el framework Tiempo de uso de Apple para bloquear los
sitios web y las apps que elijas. Las apps que eliges son selecciones opacas
del sistema que permanecen en el dispositivo; Posato no recibe de Tiempo de
uso nombres de apps ni datos de uso. Para que un horario pueda empezar con
Posato cerrado, Posato guarda una copia de tus horarios y de los sitios web y
las apps elegidas de cada conjunto de pausa en un almacenamiento compartido
solo con su propia extensión de Tiempo de uso en el mismo iPhone. Esa copia
nunca sale del dispositivo, se excluye de las copias de seguridad del
dispositivo y se elimina cuando se eliminan los datos de Posato o el espacio
de trabajo.

## Bloqueo en el Mac

En el Mac, el asistente en segundo plano de Posato bloquea los sitios web
elegidos dirigiendo el tráfico del navegador a través de un proxy local en tu
Mac mientras hay una sesión activa. El asistente examina cada solicitud solo
el tiempo necesario para permitirla o bloquearla, y no la guarda ni la envía.
Para mostrar su página de pausa en la pestaña actual de Safari o Chrome,
Posato puede usar el permiso de Automatización; solo lee la dirección de la
pestaña actual en ese momento y no guarda ningún registro. Posato no descifra
el tráfico HTTPS.

Si eliges empezar sesiones en un Mac sin la contraseña de administrador, el
asistente guarda un registro de ese permiso en un archivo del sistema que solo
el sistema puede leer. El registro contiene el número de usuario y el
identificador de sistema de tu cuenta del Mac y el identificador de hardware
de tu Mac. Nunca sale de tu Mac y se elimina cuando desactivas la opción o
eliminas Posato de este Mac.

## Bloqueo en Linux

En Linux, Posato instala, una sola vez y con tu contraseña, un servicio de
pausa que se ejecuta con permisos del sistema. Solo tu cuenta de usuario y el
sistema pueden enviarle solicitudes. Mientras hay una pausa en curso, el
servicio:

- añade cada sitio web en pausa y su nombre `www` al archivo del sistema
  `/etc/hosts`, que pueden leer las demás cuentas de usuario del ordenador, y
  los quita cuando termina la pausa;
- cierra los programas en pausa que elegiste y que se ejecutan con tu cuenta
  de usuario, comprobando qué programa es cada proceso en ejecución, sin leer
  ni guardar lo que haces en él.

El servicio guarda tu nombre de usuario y tu número de usuario en un archivo
de configuración del sistema, y la pausa que aplica, con sus sitios web y las
ubicaciones de los programas, en un archivo del sistema que solo el sistema
puede leer, para que la pausa se mantenga tras un reinicio. Cuando termina la
pausa, ese archivo solo conserva el identificador de la sesión hasta que
Posato lo lee. Desinstalar el paquete elimina el servicio, restaura
`/etc/hosts` y elimina ambos archivos. Nada de lo que maneja el servicio sale
de tu ordenador.

## Bloqueo en Android

En Android, Posato necesita varios permisos, que se conceden en los propios
ajustes de Android:

- **VPN.** Mientras hay una pausa con sitios web en curso, Posato ejecuta una
  VPN local que solo transporta consultas de nombres, las preguntas que hace
  tu teléfono para encontrar la dirección de un sitio web. Responde él mismo a
  los nombres en pausa y pasa todas las demás consultas al servidor DNS de la
  red en la que estás, o a Google Public DNS cuando esa red no ofrece ninguno
  que Posato pueda usar. Ningún otro tráfico entra en la VPN, y Posato no
  registra ni guarda las consultas.
- **Acceso a datos de uso.** Mientras hay una pausa con apps en curso, Posato
  comprueba unas dos veces por segundo qué app está en primer plano, para
  poder cubrir una app en pausa con su pantalla de bloqueo. Solo conserva la
  app que está en primer plano en ese momento, en memoria, y no registra el
  uso de las apps.
- **Mostrar sobre otras aplicaciones**, para mostrar la pantalla de bloqueo.
- **Notificaciones, alarmas exactas y ejecución sin restricciones de
  batería**, para que las pausas y los horarios empiecen y terminen a su hora
  con Posato cerrado. Mientras Posato los mantiene en marcha, se muestra una
  notificación silenciosa.
- **Acceso a todos los archivos**, para que Posato pueda leer y escribir en la
  carpeta que elijas para la sincronización por carpeta. Posato solo lee y
  escribe en esa carpeta.

Posato lee la lista de apps que puedes abrir desde el launcher, para que
puedas elegir apps que pausar; la lista permanece en el teléfono. La pausa que
aplica, con sus sitios web y los nombres de paquete de las apps, se guarda en
el almacenamiento privado de Posato hasta que termina la pausa, para que se
mantenga tras un reinicio.

## Exportación e importación

Puedes exportar tus conjuntos de pausa a un archivo e importarlo en otro
dispositivo. El archivo contiene los nombres y los sitios web de tus conjuntos
de pausa, cuál es el predeterminado y tus horarios. Nunca contiene las apps
elegidas, las sesiones, el historial, los nombres de los dispositivos ni las
claves de sincronización. Posato solo escribe el archivo donde tú lo guardas o
lo compartes, y no lo envía a ningún sitio por sí mismo.

- Sin código, cualquiera que consiga el archivo puede leer tus conjuntos de
  pausa y tus sitios web.
- Con **Proteger con un código**, el archivo se cifra con una clave derivada
  de un código de nueve caracteres que Posato muestra una vez y no guarda. El
  código frena a un lector casual de un archivo que llega a la persona
  equivocada, pero alguien que tenga el archivo y dedique mucha potencia de
  cálculo a adivinarlo podría abrirlo, así que envía el archivo y el código
  solo adonde confíes.
- En el iPhone, un archivo compartido es una copia temporal que Posato elimina
  cuando se cierra la hoja de compartir, y un archivo importado se lee desde
  una copia temporal que Posato elimina después de leerla.

Una importación cambia tus conjuntos de pausa y horarios igual que si los
editaras a mano; si la sincronización está activada, los cambios llegan a tus
otros dispositivos.

## Notificaciones

En el Mac y el iPhone, Posato puede mostrar avisos locales cuando una pausa
termina o empieza en otro dispositivo. Los crea en tu dispositivo el sistema
de notificaciones de tu dispositivo; Posato no envía nada a ningún servidor
para ellos. Puedes desactivarlos en Acerca de Posato o en los ajustes de tu
dispositivo.

## Actualizaciones en el Mac

Posato para iPhone, Linux y Android no busca actualizaciones por sí mismo. En
el Mac, Posato pregunta antes de buscar actualizaciones automáticamente. Si lo
aceptas, las busca una vez al día mientras la app está en marcha. Puedes
desactivar la búsqueda automática o elegir Buscar actualizaciones tú mismo.
Cada actualización requiere que actúes para descargarla e instalarla, y la
instalación espera hasta que no haya ninguna sesión activa.

La información de actualización y las descargas proceden de GitHub Releases a
través de Sparkle, una biblioteca de actualizaciones incluida en Posato. Las
solicitudes no contienen tu lista de sitios web, las apps elegidas, tus
sesiones, tus datos de iCloud, un identificador de instalación ni un perfil del
sistema. Posato compara las versiones y los requisitos del sistema en tu Mac.

GitHub y sus proveedores de distribución pueden recibir tu dirección IP, la
hora de la solicitud, el recurso solicitado e información técnica de la
conexión. Una dirección de descarga identifica la versión que solicitas.
Tratan esta información conforme a la
[declaración de privacidad de GitHub](https://docs.github.com/en/site-policy/privacy-policies/github-general-privacy-statement).
GitHub publica recuentos agregados de descargas; Posato no añade analítica de
uso ni identificadores de seguimiento. Abrir en tu navegador un enlace a una
versión o de soporte está sujeto a las políticas de ese sitio web y a los
ajustes de tu navegador.

Las preferencias de actualización se guardan en este Mac y no se sincronizan a
través de iCloud. Sparkle guarda localmente el estado de la búsqueda de
actualizaciones y los archivos temporales de actualización.

## Diagnóstico

Posato no recopila ni sube diagnósticos, informes de fallos ni telemetría.
Aparte de la sincronización que elijas, las consultas que la VPN de Android
transmite y las solicitudes de actualización descritas en Actualizaciones en
el Mac, Posato no hace ninguna solicitud de red propia. Tu sistema operativo o
tu tienda de apps pueden recopilar diagnósticos según los ajustes de tu
dispositivo y sus propias políticas; Posato no accede a ellos ni añade nada.

Si pides ayuda, comparte solo lo que decidas. No incluyas nombres de sitios
web, nombres de apps, datos del dispositivo, capturas de pantalla ni registros
en los informes públicos.

## Menores

Posato no está pensado para gestionar el dispositivo de otra persona y no
recopila a sabiendas datos de nadie.

## Cambios

Los cambios en esta política se publicarán en la misma dirección con una nueva
fecha de entrada en vigor.

## Contacto

Preguntas sobre privacidad: privacy@posato.app
