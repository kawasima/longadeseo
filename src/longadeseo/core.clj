(ns longadeseo.core
  (:import [org.alfresco.jlan.server.config ServerConfiguration]
           [org.alfresco.jlan.smb.server SMBServer CIFSConfigSection]
           [org.alfresco.jlan.netbios.server NetBIOSNameServer]
           [org.alfresco.jlan.server.filesys FilesystemsConfigSection]
           [org.alfresco.jlan.debug Debug]
           [java.net InetAddress]))

(def server-config (ServerConfiguration. "samba"))
(def cifs-config (CIFSConfigSection. server-config))
(doto cifs-config
  (.setSessionPort 1139)
  (.setNameServerPort 1137)
  (.setDatagramPort 1138)
  (.setTcpipSMBPort 1445))
(def filesys-config (FilesystemsConfigSection. server-config))


(defn create-smb-server [server-config]
  (let [smb-server (SMBServer. server-config)]
    smb-server))

(when (.hasNetBIOSSMB cifs-config)
  (.addServer server-config (NetBIOSNameServer. server-config)))

(. cifs-config getSessionPort)
(.addServer server-config (create-smb-server server-config))
(..  (.getServer server-config 0) getFilesystemConfiguration)
(.startServer (.getServer server-config 0))
