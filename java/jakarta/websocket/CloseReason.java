/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package jakarta.websocket;

public class CloseReason {

    private final CloseCode closeCode;
    private final String reasonPhrase;

    public CloseReason(CloseReason.CloseCode closeCode, String reasonPhrase) {
        this.closeCode = closeCode;
        this.reasonPhrase = reasonPhrase;
    }

    public CloseCode getCloseCode() {
        return closeCode;
    }

    public String getReasonPhrase() {
        return reasonPhrase;
    }

    @Override
    public String toString() {
        return "CloseReason: code [" + closeCode.getCode() + "], reason [" + reasonPhrase + "]";
    }

    public interface CloseCode {
        int getCode();
    }

    public enum CloseCodes implements CloseReason.CloseCode {

        NORMAL_CLOSURE(1000),
        GOING_AWAY(1001),
        PROTOCOL_ERROR(1002),
        CANNOT_ACCEPT(1003),
        RESERVED(1004),
        NO_STATUS_CODE(1005),
        CLOSED_ABNORMALLY(1006),
        NOT_CONSISTENT(1007),
        VIOLATED_POLICY(1008),
        TOO_BIG(1009),
        NO_EXTENSION(1010),
        UNEXPECTED_CONDITION(1011),
        SERVICE_RESTART(1012),
        TRY_AGAIN_LATER(1013),
        TLS_HANDSHAKE_FAILURE(1015);

        private final int code;

        CloseCodes(int code) {
            this.code = code;
        }

        public static CloseCode getCloseCode(final int code) {
            if (code > 2999 && code < 5000) {
                return () -> code;
            }
            return switch (code) {
                case 1000 -> NORMAL_CLOSURE;
                case 1001 -> GOING_AWAY;
                case 1002 -> PROTOCOL_ERROR;
                case 1003 -> CANNOT_ACCEPT;
                case 1004 -> RESERVED;
                case 1005 -> NO_STATUS_CODE;
                case 1006 -> CLOSED_ABNORMALLY;
                case 1007 -> NOT_CONSISTENT;
                case 1008 -> VIOLATED_POLICY;
                case 1009 -> TOO_BIG;
                case 1010 -> NO_EXTENSION;
                case 1011 -> UNEXPECTED_CONDITION;
                case 1012 -> SERVICE_RESTART;
                case 1013 -> TRY_AGAIN_LATER;
                case 1015 -> TLS_HANDSHAKE_FAILURE;
                default -> throw new IllegalArgumentException("Invalid close code: [" + code + "]");
            };
        }

        @Override
        public int getCode() {
            return code;
        }
    }
}
