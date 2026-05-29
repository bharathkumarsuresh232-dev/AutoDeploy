package com.sterling.app.exception;

/**
 * Deployment Exception
 * 
 * Custom exception for deployment-related errors.
 * 
 * @author Sterling Development Team
 * @version 1.0.0
 */
public class DeploymentException extends RuntimeException {
    
    public DeploymentException(String message) {
        super(message);
    }
    
    public DeploymentException(String message, Throwable cause) {
        super(message, cause);
    }
}