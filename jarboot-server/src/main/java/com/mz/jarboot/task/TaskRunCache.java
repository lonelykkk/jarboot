package com.mz.jarboot.task;

import com.mz.jarboot.api.pojo.ServiceGroup;
import com.mz.jarboot.api.pojo.ServiceSetting;
import com.mz.jarboot.base.AgentManager;
import com.mz.jarboot.common.CacheDirHelper;
import com.mz.jarboot.common.notify.AbstractEventRegistry;
import com.mz.jarboot.common.pojo.ResultCodeConst;
import com.mz.jarboot.common.JarbootException;
import com.mz.jarboot.api.constant.CommonConst;
import com.mz.jarboot.api.pojo.ServiceInstance;
import com.mz.jarboot.common.notify.NotifyReactor;
import com.mz.jarboot.common.utils.StringUtils;
import com.mz.jarboot.utils.PropertyFileUtils;
import com.mz.jarboot.utils.SettingUtils;
import com.mz.jarboot.ws.WebSocketMainServer;
import com.mz.jarboot.common.utils.VMUtils;
import org.apache.commons.io.FileUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

import javax.annotation.PostConstruct;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @author majianzheng
 */
@Component
public class TaskRunCache {
    /** 需要排除的工作空间里的目录 */
    @Value("${jarboot.services.exclude-dirs:bin,lib,conf,plugins,plugin}")
    private String excludeDirs;
    @Autowired
    private AbstractEventRegistry eventRegistry;

    /** 需要排除的工作空间里的目录 */
    private final HashSet<String> excludeDirSet = new HashSet<>(16);
    /** 正在启动中的服务 */
    private final ConcurrentHashMap<String, Long> startingCache = new ConcurrentHashMap<>(16);
    /** 正在停止中的服务 */
    private final ConcurrentHashMap<String, Long> stoppingCache = new ConcurrentHashMap<>(16);

    /**
     * 获取服务名称列表
     * @return 服务名称列表
     */
    public List<String> getServiceNameList(String username) {
        File[] serviceDirs = this.getServiceDirs(username);
        List<String> paths = new ArrayList<>();
        if (null != serviceDirs && serviceDirs.length > 0) {
            for (File f : serviceDirs) {
                paths.add(f.getName());
            }
        }
        return paths;
    }

    /**
     * 获取服务目录列表
     * @return 服务目录
     */
    public File[] getServiceDirs(String userDir) {
        String workspace = SettingUtils.getWorkspace();
        File servicesDir = new File(workspace, userDir);
        if (!servicesDir.isDirectory() || !servicesDir.exists()) {
            if (!servicesDir.mkdirs()) {
                throw new JarbootException(ResultCodeConst.INTERNAL_ERROR, workspace + "目录创建失败");
            }
        }
        File[] serviceDirs = servicesDir.listFiles(this::filterExcludeDir);
        if (null == serviceDirs || serviceDirs.length < 1) {
            return serviceDirs;
        }
        // 根据名字排序
        Arrays.sort(serviceDirs, Comparator.comparing(File::getName));
        return serviceDirs;
    }

    public ServiceInstance getService(String userDir, File serverDir) {
        ServiceInstance process = new ServiceInstance();
        process.setName(serverDir.getName());
        String path = serverDir.getAbsolutePath();
        String sid = SettingUtils.createSid(path);
        process.setSid(sid);
        process.setGroup(this.getGroup(userDir, process.getName(), path));

        if (this.isStarting(sid)) {
            process.setStatus(CommonConst.STARTING);
        } else if (this.isStopping(sid)) {
            process.setStatus(CommonConst.STOPPING);
        } else if (AgentManager.getInstance().isOnline(sid)) {
            process.setStatus(CommonConst.RUNNING);
        } else {
            process.setStatus(CommonConst.STOPPED);
        }
        return process;
    }

    /**
     * 获取服务列表
     * @param userDir 用户目录
     * @return 服务列表
     */
    public List<ServiceInstance> getServiceList(String userDir) {
        List<ServiceInstance> serverList = new ArrayList<>();
        File[] serviceDirs = getServiceDirs(userDir);
        if (null == serviceDirs) {
            return serverList;
        }
        for (File file : serviceDirs) {
            ServiceInstance process = getService(userDir, file);
            serverList.add(process);
        }
        return serverList;
    }

    /**
     * 获取服务组
     * @param userDir 用户目录
     * @return 服务组
     */
    public ServiceGroup getServiceGroup(String userDir) {
        List<ServiceInstance> serviceList = this.getServiceList(userDir);
        ServiceGroup localGroup = new ServiceGroup();
        localGroup.setHost("localhost");
        localGroup.setChildren(new ArrayList<>());
        if (CollectionUtils.isEmpty(serviceList)) {
            return localGroup;
        }
        HashMap<String, ServiceGroup> map = new HashMap<>(16);
        List<ServiceGroup> list = new ArrayList<>();
        serviceList.forEach(service -> {
            if (StringUtils.isEmpty(service.getGroup())) {
                localGroup.getChildren().add(service);
            } else {
                map.compute(service.getGroup(), (k, v) -> {
                    if (null == v) {
                        v = new ServiceGroup();
                        v.setName(service.getGroup());
                        v.setChildren(new ArrayList<>());
                        list.add(v);
                    }
                    v.getChildren().add(service);
                    return v;
                });
            }
        });
        localGroup.getChildren().addAll(list);
        return localGroup;
    }

    public boolean hasStartingOrStopping() {
        return !this.startingCache.isEmpty() || !this.stoppingCache.isEmpty();
    }

    public boolean isStartingOrStopping(String sid) {
        return startingCache.containsKey(sid) || stoppingCache.containsKey(sid);
    }

    public boolean isStarting(String sid) {
        return startingCache.containsKey(sid);
    }

    public boolean addStarting(String sid) {
        return null == startingCache.putIfAbsent(sid, System.currentTimeMillis());
    }

    public void removeStarting(String sid) {
        startingCache.remove(sid);
    }

    public boolean isStopping(String sid) {
        return stoppingCache.containsKey(sid);
    }

    public boolean addStopping(String sid) {
        return null == stoppingCache.putIfAbsent(sid, System.currentTimeMillis());
    }

    public void removeStopping(String sid) {
        stoppingCache.remove(sid);
    }

    private String getGroup(String userDir, String serviceName, String sid) {
        ServiceSetting setting = PropertyFileUtils.getServiceSettingBySid(sid);
        if (null != setting) {
            return setting.getGroup();
        }
        setting = PropertyFileUtils.getServiceSetting(userDir, serviceName);
        if (null == setting) {
            return StringUtils.EMPTY;
        }
        return setting.getGroup();
    }

    private boolean filterExcludeDir(File dir) {
        if (!dir.isDirectory() || dir.isHidden()) {
            return false;
        }
        final String name = dir.getName();
        if (name.startsWith(CommonConst.DOT)) {
            return false;
        }
        if (StringUtils.containsWhitespace(name)) {
            return false;
        }
        return !excludeDirSet.contains(name);
    }

    private void cleanPidFiles() {
        File pidDir = CacheDirHelper.getPidDir();
        if (!pidDir.exists()) {
            return;
        }
        if (!pidDir.isDirectory()) {
            try {
                FileUtils.forceDelete(pidDir);
            } catch (Exception e) {
                //ignore
            }
            return;
        }
        Collection<File> pidFiles = FileUtils.listFiles(pidDir, new String[]{"pid"}, true);
        if (!CollectionUtils.isEmpty(pidFiles)) {
            Map<String, String> allJvmPid = VMUtils.getInstance().listVM();
            pidFiles.forEach(file -> {
                try {
                    String text = FileUtils.readFileToString(file, StandardCharsets.UTF_8);
                    if (allJvmPid.containsKey(text)) {
                        return;
                    }
                } catch (Exception exception) {
                    //ignore
                }
                FileUtils.deleteQuietly(file);
            });
        }
    }

    @PostConstruct
    public void init() {
        //清理无效的pid文件
        this.cleanPidFiles();

        if (StringUtils.isBlank(excludeDirs)) {
            return;
        }
        String[] dirs = excludeDirs.split(CommonConst.COMMA_SPLIT);
        for (String s : dirs) {
            if (!StringUtils.isBlank(s)) {
                excludeDirSet.add(s.trim());
            }
        }
        //订阅任务状态变化事件
        NotifyReactor.getInstance().registerSubscriber(new TaskStatusChangeSubscriber(this.eventRegistry), WebSocketMainServer.PUBLISHER);
    }
}
