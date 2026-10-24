package com.bitcoinrewards.nfcdisplay;

final class ScannerOperationState {
    enum Action { NONE, RESUME_SUBMISSION, RESUME_POLLING, OFFER_RETRY_OR_CANCEL_OPERATION }
    private PendingOperation operation; private boolean malformed,resumeClaimed,locallyCancelled;
    ScannerOperationState(String persisted){if(persisted==null||persisted.isEmpty())return;try{operation=PendingOperation.fromJson(persisted);}catch(Exception e){malformed=true;}}
    boolean isLocked(){return malformed||operation!=null;} boolean isMalformed(){return malformed;} boolean mayScan(){return !isLocked();}
    Action onResume(){if(operation!=null&&!resumeClaimed&&!locallyCancelled){resumeClaimed=true;return operation.phase==PendingOperation.Phase.SUBMITTING?Action.RESUME_SUBMISSION:Action.RESUME_POLLING;}return nextAction();}
    void localCancel(){if(operation!=null)locallyCancelled=true;}
    Action nextAction(){return locallyCancelled?Action.OFFER_RETRY_OR_CANCEL_OPERATION:Action.NONE;}
    boolean authoritativeStatus(String status){if("paid".equals(status)||"cancelled".equals(status)||"unpaid".equals(status)){operation=null;locallyCancelled=false;resumeClaimed=false;return true;}return false;}
}
