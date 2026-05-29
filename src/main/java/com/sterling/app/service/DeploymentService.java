package com.sterling.app.service;

import com.sterling.app.model.DeploymentResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.*;
import java.time.LocalDateTime;
import java.util.concurrent.TimeUnit;

/**
 * Deployment Service
 * 
 * Core service that orchestrates the entire deployment process including:
 * - Git repository operations (checkout, pull)
 * - Maven build and packaging
 * - JAR file handling
 * - Application process management (kill, start)
 * - Logging and error handling
 * 
 * @author Sterling Development Team
 * @version 1.0.0
 */
@Slf4j
@Service
public class DeploymentService {

    @Value("${app.deployment.git.repository-path}")
    private String gitRepositoryPath;

    @Value("${app.deployment.git.timeout-seconds}")
    private long gitTimeout;

    @Value("${app.deployment.maven.command}")
    private String mavenCommand;

    @Value("${app.deployment.maven.timeout-seconds}")
    private long mavenTimeout;

    @Value("${app.deployment.deployment.beta-path}")
    private String betaPath;

    @Value("${app.deployment.deployment.prod-path}")
    private String prodPath;

    @Value("${app.deployment.deployment.log-path}")
    private String logPath;

    @Value("${app.deployment.process.kill-timeout-seconds}")
    private long killTimeout;

    /**
     * Execute deployment for a specific branch
     */
    public DeploymentResponse deploy(String branch) {
        log.info("Starting deployment process for branch: {}", branch);
        LocalDateTime startTime = LocalDateTime.now();
        
        try {
            // Step 1: Git Operations
            log.debug("Step 1: Performing Git operations for branch: {}", branch);
            gitCheckoutAndPull(branch);
            log.info("Git operations completed successfully");

            // Step 2: Maven Build
            log.debug("Step 2: Building application using Maven");
            String jarFileName = buildWithMaven();
            log.info("Maven build completed successfully. JAR file: {}", jarFileName);

            // Step 3: Determine deployment path
            String deploymentPath = getDeploymentPath(branch);
            log.debug("Step 3: Deployment path determined: {}", deploymentPath);

            // Step 4: Copy JAR to deployment directory
            log.debug("Step 4: Copying JAR file to deployment directory");
            copyJarToDeploymentPath(jarFileName, deploymentPath);
            log.info("JAR file copied successfully to: {}", deploymentPath);

            // Step 5: Kill existing process
            log.debug("Step 5: Killing existing process");
            killExistingProcess(deploymentPath);
            log.info("Existing process terminated");

            // Step 6: Start new application
            log.debug("Step 6: Starting new application instance");
            startApplication(deploymentPath, jarFileName);
            log.info("New application instance started successfully");

            log.info("Deployment completed successfully for branch: {} in {}", 
                branch, calculateDuration(startTime));
            
            return new DeploymentResponse(
                "SUCCESS",
                "Deployment completed successfully for branch: " + branch,
                startTime,
                LocalDateTime.now()
            );
        } catch (Exception e) {
            log.error("Deployment failed for branch: {}", branch, e);
            return new DeploymentResponse(
                "FAILED",
                "Deployment failed: " + e.getMessage(),
                startTime,
                LocalDateTime.now()
            );
        }
    }

    /**
     * Git Checkout and Pull
     */
    private void gitCheckoutAndPull(String branch) throws IOException, InterruptedException {
        log.debug("Executing git checkout for branch: {}", branch);
        
        String checkoutCommand = "git -C " + gitRepositoryPath + " checkout " + branch;
        executeCommand(checkoutCommand, gitTimeout);
        
        log.debug("Executing git pull for branch: {}", branch);
        String pullCommand = "git -C " + gitRepositoryPath + " pull origin " + branch;
        executeCommand(pullCommand, gitTimeout);
        
        log.info("Git checkout and pull completed for branch: {}", branch);
    }

    /**
     * Build Application with Maven
     */
    private String buildWithMaven() throws IOException, InterruptedException {
        log.debug("Starting Maven build in directory: {}", gitRepositoryPath);
        
        String command = "bash -c 'cd " + gitRepositoryPath + " && " + mavenCommand + "'";
        executeCommand(command, mavenTimeout);
        
        String findJarCommand = "find " + gitRepositoryPath + "/target -name 'auto-deploy*.jar' -type f | head -1";
        String jarPath = executeCommandWithOutput(findJarCommand, gitTimeout);
        
        if (jarPath == null || jarPath.trim().isEmpty()) {
            throw new RuntimeException("Maven build did not generate expected JAR file");
        }
        
        log.info("Maven build successful. JAR location: {}", jarPath);
        return jarPath.trim();
    }

    /**
     * Get Deployment Path based on Branch
     */
    private String getDeploymentPath(String branch) {
        if ("main".equalsIgnoreCase(branch) || "master".equalsIgnoreCase(branch)) {
            return prodPath;
        } else if ("beta".equalsIgnoreCase(branch)) {
            return betaPath;
        } else {
            log.warn("Unknown branch: {}. Using beta deployment path as default", branch);
            return betaPath;
        }
    }

    /**
     * Copy JAR to Deployment Path
     */
    private void copyJarToDeploymentPath(String jarPath, String deploymentPath) 
            throws IOException, InterruptedException {
        log.debug("Copying JAR from {} to {}", jarPath, deploymentPath);
        
        String mkdirCommand = "mkdir -p " + deploymentPath;
        executeCommand(mkdirCommand, 30);
        
        String copyCommand = "cp " + jarPath + " " + deploymentPath + "/auto-deploy.jar";
        executeCommand(copyCommand, 60);
        
        log.info("JAR file copied successfully to: {}", deploymentPath);
    }

    /**
     * Kill Existing Process
     */
    private void killExistingProcess(String deploymentPath) throws IOException, InterruptedException {
        log.debug("Attempting to kill existing process in: {}", deploymentPath);
        
        try {
            String findPidCommand = "pgrep -f 'java -jar " + deploymentPath + "'";
            String pid = executeCommandWithOutput(findPidCommand, 10);
            
            if (pid != null && !pid.trim().isEmpty()) {
                log.debug("Found running process with PID: {}", pid);
                String killCommand = "kill -9 " + pid.trim();
                executeCommand(killCommand, killTimeout);
                log.info("Process {} killed successfully", pid.trim());
                
                Thread.sleep(2000);
            } else {
                log.debug("No existing process found");
            }
        } catch (Exception e) {
            log.warn("Failed to kill existing process: {}", e.getMessage());
        }
    }

    /**
     * Start Application
     */
    private void startApplication(String deploymentPath, String jarFileName) 
            throws IOException, InterruptedException {
        log.debug("Starting new application from: {}", deploymentPath);
        
        String mkdirCommand = "mkdir -p " + logPath;
        executeCommand(mkdirCommand, 30);
        
        String startCommand = "nohup java -jar " + deploymentPath + "/auto-deploy.jar " +
                            "> " + logPath + "/application-startup.log 2>&1 &";
        executeCommand(startCommand, 60);
        
        Thread.sleep(3000);
        
        String verifyCommand = "pgrep -f 'java -jar " + deploymentPath + "'";
        String pid = executeCommandWithOutput(verifyCommand, 10);
        
        if (pid != null && !pid.trim().isEmpty()) {
            log.info("Application started successfully with PID: {}", pid.trim());
        } else {
            throw new RuntimeException("Application failed to start");
        }
    }

    /**
     * Execute Shell Command
     */
    private void executeCommand(String command, long timeoutSeconds) 
            throws IOException, InterruptedException {
        log.debug("Executing command: {}", command);
        
        ProcessBuilder processBuilder = new ProcessBuilder("/bin/bash", "-c", command);
        processBuilder.redirectErrorStream(true);
        
        Process process = processBuilder.start();
        boolean completed = process.waitFor(timeoutSeconds, TimeUnit.SECONDS);
        
        if (!completed) {
            process.destroy();
            throw new InterruptedException("Command execution timeout after " + timeoutSeconds + " seconds");
        }
        
        if (process.exitValue() != 0) {
            String error = readInputStream(process.getErrorStream());
            throw new RuntimeException("Command execution failed with exit code: " + process.exitValue() + ". Error: " + error);
        }
        
        log.debug("Command executed successfully");
    }

    /**
     * Execute Shell Command and Return Output
     */
    private String executeCommandWithOutput(String command, long timeoutSeconds) 
            throws IOException, InterruptedException {
        log.debug("Executing command with output: {}", command);
        
        ProcessBuilder processBuilder = new ProcessBuilder("/bin/bash", "-c", command);
        processBuilder.redirectErrorStream(true);
        
        Process process = processBuilder.start();
        boolean completed = process.waitFor(timeoutSeconds, TimeUnit.SECONDS);
        
        if (!completed) {
            process.destroy();
            throw new InterruptedException("Command execution timeout after " + timeoutSeconds + " seconds");
        }
        
        String output = readInputStream(process.getInputStream());
        
        if (process.exitValue() != 0) {
            log.warn("Command execution returned non-zero exit code: {}", process.exitValue());
        }
        
        return output;
    }

    /**
     * Read Input Stream
     */
    private String readInputStream(InputStream inputStream) throws IOException {
        StringBuilder output = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream))) {
            String line;
            while ((line = reader.readLine()) != null) {
                output.append(line).append("\n");
            }
        }
        return output.toString();
    }

    /**
     * Calculate Duration
     */
    private String calculateDuration(LocalDateTime startTime) {
        long seconds = java.time.temporal.ChronoUnit.SECONDS.between(startTime, LocalDateTime.now());
        long minutes = seconds / 60;
        long secs = seconds % 60;
        return minutes + "m " + secs + "s";
    }
}