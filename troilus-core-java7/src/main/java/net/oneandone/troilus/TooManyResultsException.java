/*
 * Copyright 1&1 Internet AG, https://github.com/1and1/
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package net.oneandone.troilus;




/**
 * Exception thrown when a more results (rows) are returned than expected
 */
public class TooManyResultsException extends RuntimeException {

    private static final long serialVersionUID = 4270476820995364200L;

    private transient final Result result;
    
    /**
     * @param message the message to report
     * @param result  the result
     */
    public TooManyResultsException(Result result, String message) {
        super(message);
        this.result = result;
    }
    
    /**
     * @return the result
     */
    public Result getResult() {
        return result;
    }
}