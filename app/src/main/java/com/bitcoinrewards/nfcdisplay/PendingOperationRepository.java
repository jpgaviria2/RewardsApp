package com.bitcoinrewards.nfcdisplay;

final class PendingOperationRepository {
    interface Storage { boolean commit(String value); String read(); boolean clear(); }
    static final class LoadResult { final PendingOperation operation; final boolean malformed; LoadResult(PendingOperation operation,boolean malformed){this.operation=operation;this.malformed=malformed;} }
    private final Storage storage; private boolean failedClosed;
    PendingOperationRepository(Storage storage){this.storage=storage;}
    static boolean isLocked(LoadResult result){return result!=null&&(result.malformed||result.operation!=null);}
    boolean save(PendingOperation operation){String encoded=operation.toJson().toString();if(!storage.commit(encoded)){failedClosed=true;return false;}String actual=storage.read();if(!encoded.equals(actual)){failedClosed=true;return false;}failedClosed=false;return true;}
    LoadResult load(){if(failedClosed)return new LoadResult(null,true);String raw=storage.read();if(raw==null||raw.isEmpty())return new LoadResult(null,false);try{return new LoadResult(PendingOperation.fromJson(raw),false);}catch(Exception e){return new LoadResult(null,true);}}
    boolean clearAuthoritative(){if(!storage.clear()){failedClosed=true;return false;}failedClosed=storage.read()!=null;return !failedClosed;}
}
