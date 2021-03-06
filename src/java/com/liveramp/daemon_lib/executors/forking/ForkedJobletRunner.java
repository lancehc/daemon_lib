package com.liveramp.daemon_lib.executors.forking;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.Map;

import com.google.common.io.ByteStreams;
import org.apache.commons.io.FileUtils;

import com.liveramp.daemon_lib.Joblet;
import com.liveramp.daemon_lib.JobletConfig;
import com.liveramp.daemon_lib.JobletFactory;
import com.liveramp.daemon_lib.executors.processes.ProcessUtil;
import com.liveramp.daemon_lib.tracking.DefaultJobletStatusManager;
import com.liveramp.daemon_lib.utils.DaemonException;
import com.liveramp.daemon_lib.utils.JobletConfigStorage;
import com.liveramp.java_support.RunJarUtil;

public class ForkedJobletRunner implements ProcessJobletRunner<Integer> {
  private static final String JOBLET_RUNNER_SCRIPT = "bin/joblet_runner.sh";
  private static final String JOBLET_RUNNER_SCRIPT_SOURCE = "com/liveramp/daemon_lib/utils/joblet_runner.txt";

  @Override
  public Integer run(Class<? extends JobletFactory<? extends JobletConfig>> jobletFactoryClass, JobletConfigStorage configStore, String cofigIdentifier, Map<String, String> envVariables, String workingDir) throws IOException, ClassNotFoundException {
    prepareScript();

    URL launchJar = RunJarUtil.getLaunchJarURL();
    if(launchJar == null){
      throw new RuntimeException("Cannot find main jar path!");
    }

    envVariables.put("JOBJAR", launchJar.getPath());

    ProcessBuilder processBuilder = new ProcessBuilder(JOBLET_RUNNER_SCRIPT, quote(jobletFactoryClass.getName()), configStore.getPath(), workingDir, cofigIdentifier);
    processBuilder.environment().putAll(envVariables);
    int pid = ProcessUtil.run(processBuilder);

    return pid;
  }

  private static void prepareScript() throws IOException {
    File productionScript = new File(JOBLET_RUNNER_SCRIPT);
    if (!productionScript.exists()) {
      InputStream scriptResourceInput = ForkedJobletRunner.class.getClassLoader().getResourceAsStream(JOBLET_RUNNER_SCRIPT_SOURCE);

      final File scriptParentDir = productionScript.getParentFile();
      try {
        FileUtils.forceMkdir(scriptParentDir);
      } catch (IOException e) {
        throw new IOException(String.format("Could not create directory for Joblet Runner script at %s", scriptParentDir.getAbsolutePath()), e);
      }

      FileOutputStream scriptProductionOutput = new FileOutputStream(JOBLET_RUNNER_SCRIPT);

      ByteStreams.copy(scriptResourceInput, scriptProductionOutput);

      scriptResourceInput.close();
      scriptProductionOutput.close();

      productionScript.setExecutable(true, false);
    }
  }

  public static String quote(String s) {
    return "'" + s + "'";
  }

  private static String unquote(String s) {
    if (s.charAt(0) == '\'' && s.charAt(s.length() - 1) == '\'') {
      return s.substring(1, s.length() - 1);
    } else {
      return s;
    }
  }

  public static void main(String[] args) throws IOException, ClassNotFoundException, IllegalAccessException, InstantiationException, DaemonException {

    String jobletFactoryClassName = unquote(args[0]);
    String configStorePath = args[1];
    String daemonWorkingDir = args[2];
    String id = args[3];

    JobletFactory factory = (JobletFactory)Class.forName(jobletFactoryClassName).newInstance();
    JobletConfig config = JobletConfigStorage.production(configStorePath).loadConfig(id);
    DefaultJobletStatusManager jobletStatusManager = new DefaultJobletStatusManager(daemonWorkingDir);

    jobletStatusManager.start(id);
    Joblet joblet = factory.create(config);
    joblet.run();
    jobletStatusManager.complete(id);
  }
}
