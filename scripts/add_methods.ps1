# Add FIX Session Handling methods to OrderApplication.java
$filePath = "src/main/java/com/stocker/OrderApplication.java"
$content = Get-Content $filePath -Raw

# Methods to add before the final closing brace
$newMethods = @"

    private void sendResendRequest(SessionID sessionId, int beginSeqNo, int endSeqNo) {
        try {
            quickfix.fix44.ResendRequest resendReq = new quickfix.fix44.ResendRequest();
            resendReq.set(new BeginSeqNo(beginSeqNo));
            resendReq.set(new EndSeqNo(endSeqNo));
            Session.sendToTarget(resendReq, sessionId);
            System.out.println("[SESSION] [RESEND] ResendRequest sent FROM: " + beginSeqNo + " TO: " + (endSeqNo == 0 ? "CURRENT" : endSeqNo));
        } catch (Exception e) {
            System.err.println("[SESSION] ERROR: Failed to send ResendRequest: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void handleResendRequest(Message message, SessionID sessionId) {
        try {
            int beginSeqNo = message.getInt(BeginSeqNo.FIELD);
            int endSeqNo = message.getInt(EndSeqNo.FIELD);
            System.out.println("[SESSION] [REPLAY] Received ResendRequest FROM: " + beginSeqNo + " TO: " + (endSeqNo == 0 ? "CURRENT" : endSeqNo));
            System.out.println("[SESSION] [REPLAY] Message replay not implemented in this version");
        } catch (Exception e) {
            System.err.println("[SESSION] ERROR: Failed to handle ResendRequest: " + e.getMessage());
            e.printStackTrace();
        }
    }
"@

# Replace the final closing brace with methods + brace
$content = $content -replace '(\s*)\}\s*$', "$newMethods`n}`n"
$content | Set-Content $filePath
Write-Host "[OK] Added ResendRequest handler methods to OrderApplication.java"
