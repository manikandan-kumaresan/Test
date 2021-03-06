/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.hadoop.hdfs.server.protocolR23Compatible;

import java.io.IOException;

import org.apache.avro.reflect.Nullable;
import org.apache.hadoop.classification.InterfaceAudience;
import org.apache.hadoop.hdfs.DFSConfigKeys;
import org.apache.hadoop.hdfs.protocol.ClientProtocol;
import org.apache.hadoop.hdfs.protocol.LocatedBlock;
import org.apache.hadoop.hdfs.protocolR23Compatible.ClientNamenodeWireProtocol;
import org.apache.hadoop.hdfs.protocolR23Compatible.DatanodeIDWritable;
import org.apache.hadoop.hdfs.protocolR23Compatible.ExtendedBlockWritable;
import org.apache.hadoop.hdfs.protocolR23Compatible.LocatedBlockWritable;
import org.apache.hadoop.hdfs.protocolR23Compatible.NamespaceInfoWritable;
import org.apache.hadoop.hdfs.protocolR23Compatible.ProtocolSignatureWritable;
import org.apache.hadoop.ipc.VersionedProtocol;
import org.apache.hadoop.security.KerberosInfo;

/**********************************************************************
 * Protocol that a DFS datanode uses to communicate with the NameNode.
 * It's used to upload current load information and block reports.
 *
 * The only way a NameNode can communicate with a DataNode is by
 * returning values from these functions.
 *
 **********************************************************************/
@KerberosInfo(
    serverPrincipal = DFSConfigKeys.DFS_NAMENODE_USER_NAME_KEY, 
    clientPrincipal = DFSConfigKeys.DFS_DATANODE_USER_NAME_KEY)
@InterfaceAudience.Private
public interface DatanodeWireProtocol extends VersionedProtocol {
  /**
   * The  rules for changing this protocol are the same as that for
   * {@link ClientNamenodeWireProtocol} - see that java file for details.
   */
  public static final long versionID = 28L;
  
  // error code
  final static int NOTIFY = 0;
  final static int DISK_ERROR = 1; // there are still valid volumes on DN
  final static int INVALID_BLOCK = 2;
  final static int FATAL_DISK_ERROR = 3; // no valid volumes left on DN

  /**
   * Determines actions that data node should perform 
   * when receiving a datanode command. 
   */
  final static int DNA_UNKNOWN = 0;    // unknown action   
  final static int DNA_TRANSFER = 1;   // transfer blocks to another datanode
  final static int DNA_INVALIDATE = 2; // invalidate blocks
  final static int DNA_SHUTDOWN = 3;   // shutdown node
  final static int DNA_REGISTER = 4;   // re-register
  final static int DNA_FINALIZE = 5;   // finalize previous upgrade
  final static int DNA_RECOVERBLOCK = 6;  // request a block recovery
  final static int DNA_ACCESSKEYUPDATE = 7;  // update access key
  final static int DNA_BALANCERBANDWIDTHUPDATE = 8; // update balancer bandwidth
  
  /** 
   * Register Datanode.
   * @return updated {@link DatanodeRegistrationWritable}, which contains 
   * new storageID if the datanode did not have one and
   * registration ID for further communication.
   */
  public DatanodeRegistrationWritable registerDatanode(
      DatanodeRegistrationWritable registration) throws IOException;
  /**
   * sendHeartbeat() tells the NameNode that the DataNode is still
   * alive and well.  Includes some status info, too. 
   * It also gives the NameNode a chance to return 
   * an array of "DatanodeCommand" objects.
   * A DatanodeCommand tells the DataNode to invalidate local block(s), 
   * or to copy them to other DataNodes, etc.
   * @param registration datanode registration information
   * @param capacity total storage capacity available at the datanode
   * @param dfsUsed storage used by HDFS
   * @param remaining remaining storage available for HDFS
   * @param blockPoolUsed storage used by the block pool
   * @param xmitsInProgress number of transfers from this datanode to others
   * @param xceiverCount number of active transceiver threads
   * @param failedVolumes number of failed volumes
   * @throws IOException on error
   */
  @Nullable
  public DatanodeCommandWritable[] sendHeartbeat(
      DatanodeRegistrationWritable registration, long capacity, long dfsUsed,
      long remaining, long blockPoolUsed, int xmitsInProgress,
      int xceiverCount, int failedVolumes) throws IOException;

  /**
   * blockReport() tells the NameNode about all the locally-stored blocks.
   * The NameNode returns an array of Blocks that have become obsolete
   * and should be deleted.  This function is meant to upload *all*
   * the locally-stored blocks.  It's invoked upon startup and then
   * infrequently afterwards.
   * @param registration
   * @param poolId - the block pool ID for the blocks
   * @param blocks - the block list as an array of longs.
   *     Each block is represented as 2 longs.
   *     This is done instead of Block[] to reduce memory used by block reports.
   *     
   * @return - the next command for DN to process.
   * @throws IOException
   */
  public DatanodeCommandWritable blockReport(
      DatanodeRegistrationWritable registration, String poolId, long[] blocks)
      throws IOException;
    
  /**
   * blockReceivedAndDeleted() allows the DataNode to tell the NameNode about
   * recently-received and -deleted block data. 
   * 
   * For the case of received blocks, a hint for preferred replica to be 
   * deleted when there is any excessive blocks is provided.
   * For example, whenever client code
   * writes a new Block here, or another DataNode copies a Block to
   * this DataNode, it will call blockReceived().
   */
  public void blockReceivedAndDeleted(
      DatanodeRegistrationWritable registration, String poolId,
      ReceivedDeletedBlockInfoWritable[] receivedAndDeletedBlocks)
      throws IOException;

  /**
   * errorReport() tells the NameNode about something that has gone
   * awry.  Useful for debugging.
   */
  public void errorReport(DatanodeRegistrationWritable registration,
      int errorCode, String msg) throws IOException;
    
  public NamespaceInfoWritable versionRequest() throws IOException;

  /**
   * This is a very general way to send a command to the name-node during
   * distributed upgrade process.
   * 
   * The generosity is because the variety of upgrade commands is unpredictable.
   * The reply from the name-node is also received in the form of an upgrade 
   * command. 
   * 
   * @return a reply in the form of an upgrade command
   */
  UpgradeCommandWritable processUpgradeCommand(UpgradeCommandWritable comm)
      throws IOException;
  
  /**
   * same as {@link ClientProtocol#reportBadBlocks(LocatedBlock[])}
   * }
   */
  public void reportBadBlocks(LocatedBlockWritable[] blocks) throws IOException;
  
  /**
   * Commit block synchronization in lease recovery
   */
  public void commitBlockSynchronization(ExtendedBlockWritable block,
      long newgenerationstamp, long newlength, boolean closeFile,
      boolean deleteblock, DatanodeIDWritable[] newtargets) throws IOException;
  
  /**
   * This method is defined to get the protocol signature using 
   * the R23 protocol - hence we have added the suffix of 2 the method name
   * to avoid conflict.
   */
  public ProtocolSignatureWritable getProtocolSignature2(String protocol,
      long clientVersion, int clientMethodsHash) throws IOException;
}
