(ns longadeseo.core
  (:import [org.alfresco.jlan.server.config ServerConfiguration CoreServerConfigSection SecurityConfigSection]
           [org.alfresco.jlan.smb.server SMBServer CIFSConfigSection]
           [org.alfresco.jlan.smb Dialect]
           [org.alfresco.jlan.netbios.server NetBIOSNameServer]
           [org.alfresco.jlan.server.filesys FilesystemsConfigSection VolumeInfo SrvDiskInfo DiskSharedDevice]
           [org.alfresco.jlan.server.filesys.cache StandaloneFileStateCache ]
           [org.alfresco.jlan.server.auth CifsAuthenticator UserAccount UserAccountList]
           [org.alfresco.jlan.smb.server.disk JavaFileDiskDriver]
           [org.alfresco.jlan.debug Debug]
           [org.springframework.extensions.config.element GenericConfigElement]
           [java.net InetAddress]
           [java.util Date]))

(defn dialect-selector [cifs-config]
  (let [dia-sel (.getEnabledDialects cifs-config)]
    (.ClearAll dia-sel)
    (doto dia-sel
      (.AddDialect Dialect/DOSLanMan1)
      (.AddDialect Dialect/DOSLanMan2)
      (.AddDialect Dialect/LanMan1)
      (.AddDialect Dialect/LanMan2)
      (.AddDialect Dialect/LanMan2_1)
      (.AddDialect Dialect/NT))))

(def server-config (ServerConfiguration. "samba"))
(def cifs-config (CIFSConfigSection. server-config))
(doto cifs-config
  (.setServerName "LONGADESEO")
  (.setBroadcastMask "255.255.255.255")
  (.setEnabledDialects (dialect-selector cifs-config))
  (.setSessionPort 1139)
  (.setNameServerPort 1137)
  (.setDatagramPort 1138)
  (.setTcpipSMB true)
  (.setTcpipSMBPort 1445)
  (.setHostAnnounceInterval 5)
  (.setHostAnnouncer true)
  (.setNetBIOSSMB true)
  (.setNetBIOSDebug true)
  (.setAuthenticator "org.alfresco.jlan.server.auth.EnterpriseCifsAuthenticator"
    (GenericConfigElement. "Authenticator") CifsAuthenticator/USER_MODE true))

(when (.hasSMBBindAddress cifs-config)
  (.setNetBIOSBindAddress (.getSMBBindAddress cifs-config)))

(doto (CoreServerConfigSection. server-config)
  (.setThreadPool 25 50)
  (.setMemoryPool
    (into-array Integer/TYPE [256, 4096, 16384, 66000])
    (into-array Integer/TYPE [20 20 5 5])
    (into-array Integer/TYPE [100 50 50 50])))

(defn disk-config []
  (let [config (GenericConfigElement. "disk")
        path-config (GenericConfigElement. "LocalPath")]
    (.setValue path-config ".")
    (.addChild config path-config)
    config))

(defn add-disk [filesys-config disk-name]
  (let [driver (JavaFileDiskDriver.)
        params (disk-config)
        dev-ctx (.createContext driver disk-name params)
        state-cache nil]
    (doto dev-ctx
      (.setConfigurationParameters params)
      (.enableChangeHandler true)
      (.setVolumeInformation (VolumeInfo. disk-name (mod (System/currentTimeMillis) Integer/MAX_VALUE) (Date. (System/currentTimeMillis))))
      (.setDiskInformation (SrvDiskInfo. 2560000 64 512 2304000))
      (.setShareName disk-name))
    (when (and (.requiresStateCache dev-ctx) (nil? state-cache))
      (let [state-cache (StandaloneFileStateCache.)]
        (.addStateCache dev-ctx state-cache)))
    (let [disk-dev (DiskSharedDevice. disk-name driver dev-ctx)]
      (doto disk-dev
        (.setComment "JLAN samba"))
      (if (.hasStateCache dev-ctx)
        (.addFileStateCache filesys-config name (.getStateCache dev-ctx))
        (do
          (.startFilesystem dev-ctx disk-dev)
          (when (.hasStateCache dev-ctx)
            (.. dev-ctx getStateCache (setDriverDetails disk-dev)))
          (.addShare filesys-config disk-dev))))))

(doto (FilesystemsConfigSection. server-config)
  (add-disk "share"))


(defn add-user [sec-config username password]
  (let [user (UserAccount. username password)]
    (.setMD4Password user nil)
    (when-not (.getUserAccounts sec-config)
      (.setUserAccounts sec-config (UserAccountList.)))
    (.. sec-config getUserAccounts (addUser user))))

(doto (SecurityConfigSection. server-config)
  (.setAccessControlManager "org.alfresco.jlan.server.auth.acl.DefaultAccessControlManager"
                            (GenericConfigElement. "aclManager"))
  (add-user "jlansrv" "jlan"))

(defn create-smb-server [server-config]
  (let [smb-server (SMBServer. server-config)]
    smb-server))

(defn -main [& args]
  (. cifs-config getSessionPort)
  (when (.hasNetBIOSSMB cifs-config)
    (.addServer server-config (NetBIOSNameServer. server-config)))
  (.addServer server-config (create-smb-server server-config))
  (dotimes [i (.numberOfServers server-config)]
    (.startServer (.getServer server-config i))))


