package purse;

import javacard.framework.APDU;
import javacard.framework.Applet;
import javacard.framework.ISO7816;
import javacard.framework.ISOException;
import javacard.framework.JCSystem;
import javacard.framework.Util;

/**
 * �ο�����1��http://blog.csdn.net/supergame111/article/details/5701106�������ȫ�棩
 * �ο�����2��http://blog.csdn.net/lv_victor/article/details/51028930  ���������ϸ��
 * @author yunyao
 */

public class Purse extends Applet {
	//APDU Object
	private Papdu papdu;
	
	//�ļ�ϵͳ
	private KeyFile keyfile;            //��Կ�ļ�
	private BinaryFile cardfile;       	//Ӧ�û����ļ�
	private BinaryFile personfile;     	//�ֿ��˻����ļ�
	private EPFile EPfile;              //����Ǯ���ļ�
	
	public Purse(byte[] bArray, short bOffset, byte bLength){
		papdu = new Papdu();
		// ��bArray����ռ�
		byte aidLen = bArray[bOffset];
		if(aidLen == (byte)0x00)
			register();
		else
			register(bArray, (short)(bOffset + 1), aidLen);
	}
	
	public static void install(byte[] bArray, short bOffset, byte bLength) {
		// �����ܿ���д��ĳ���ļ�
		new Purse(bArray, bOffset, bLength);
	}

	public void process(APDU apdu) {
		// ���AppletΪ�գ��˳�
		if (selectingApplet()) {
			return;
		}		
		//����1:ȡAPDU�������������ò���֮�����½�����
		byte apdu_buffer[] = apdu.getBuffer();
		//����2��ȡAPDU�����������ݷŵ�����papdu
		//��apdu��ȡ����Ƭ���������в�����data�εĳ���  
		short lc = apdu.setIncomingAndReceive();
		papdu.cla = apdu_buffer[0];  
        papdu.ins = apdu_buffer[1];  
        papdu.p1 = apdu_buffer[2];  
        papdu.p2 = apdu_buffer[3];  
        Util.arrayCopyNonAtomic(apdu_buffer, (short)5, papdu.pdata, (short)0, lc); 
		/*
		 * ����3���ж�����APDU�Ƿ�������ݶΣ����������ȡ���ݳ��ȣ�����le��ֵ
		 * ���򣬼�����Ҫlc��data�����ȡ������ԭ��lcʵ������le
		 * ��ȡle�ķ�������Ϊ��ȷ��papdu��le���֣�����IOS7816�±��ѡ�û��le���Ƿ������ݿ��е�. 
		 * ��������ݿ飬��le����buffer[ISO7816.OFFSET_CDATA+lc]  
		 * ����papdu�����ж�,����ֱ��ͨ��lc�ж�,��Ϊûlcֻ��leҲ���le����lc  
		 * 
		 * ��papdu����������ݿ�  ������Ҫ����le
		 * ���򣬽�data�ĳ���ֱ�Ӹ�ֵ��le
		 * ע��LE�ֽڱ�ʾ����������Ƭ���������ֽڳ���
		 */
		if(papdu.APDUContainData()) {
		    papdu.le = apdu_buffer[ISO7816.OFFSET_CDATA+lc];  
		    papdu.lc = apdu_buffer[ISO7816.OFFSET_LC];
		}  else {  
		    papdu.le = apdu_buffer[ISO7816.OFFSET_LC];
		    papdu.lc = 0;  
		}  
		// rc��ȡ�������ݣ��жϲ����Ƿ�ɹ�
        boolean rc = handleEvent();
		//����4:�ж��Ƿ���Ҫ�������ݣ�������apdu������	
        //if(papdu.le != 0)
        // ����ɹ����򷵻����ݣ���������apdu������
        if( rc ) {
            Util.arrayCopyNonAtomic(papdu.pdata, (short)0, apdu_buffer, (short)5, (short)papdu.pdata.length);  
            apdu.setOutgoingAndSend((short)5, papdu.le);//�ѻ����������ݷ��ظ��ն�  
        }  
	}

	/*
	 * ���ܣ�������ķ����ʹ���
	 * ��������
	 * ���أ��Ƿ�ɹ�����������
	 * ��01 Java���ܿ�֮������ P30
	 */
	private boolean handleEvent(){
		switch(papdu.ins){
			case condef.INS_CREATE_FILE:return create_file(); 				// E0 �ļ�����
			//todo�����д��������������������д��Կ����
            case (byte) 0xD4:  			return write_key();  				// D4 д��Կ
            case (byte) 0xD6:          	return write_read_bin((byte)'U');  	// D6 д�������ļ�
            case (byte) 0xB0:          	return write_read_bin((byte)'R');  	// B0���������ļ�
            /*
            case condef.INS_NIIT_TRANS:  
                if(papdu.p1 == (byte)0x00)      return init_load();  
                if(papdu.p1 == (byte)0x01)      return init_purchase();  
                ISOException.throwIt(ISO7816.SW_WRONG_P1P2);//else�׳��쳣  
            case condef.INS_LOAD:               return load();  
            case condef.INS_PURCHASE:           return purchase();  
            case condef.INS_GET_BALANCE:        return get_balance(); 
            */
		}	
		ISOException.throwIt(ISO7816.SW_INS_NOT_SUPPORTED); // 0x6D00 ��ʾ CLA���� 
		return false;
	}
	
	/*
	 * д��Կ  
	 * ��Կ�ļ��ǰ��ռ�¼��һд��ģ�Ҳ�����м�����Կ��¼������Ҫ���ͼ�����д��Կ�������
	 * �ն˷��͡�д��Կ�������Ƭִ�ж�Ӧ�Ĳ�����ȡ�������е����ݣ���д���ѽ�������Կ�ļ��С�
	 */
    private boolean write_key() {
    	
    	//ÿ��Ӧ��ֻ����һ��KEY�ļ����ұ������Ƚ���
        //��02 ����Ǯ�����ļ�ϵͳ��P19
        if(keyfile == null)
            ISOException.throwIt(ISO7816.SW_FILE_NOT_FOUND);  
        
        if( papdu.cla != (byte)0x80 || papdu.ins != (byte)0xD4)
            ISOException.throwIt(ISO7816.SW_CLA_NOT_SUPPORTED);  
  
        //��Կ�ļ���p1Ϊ0x00�� p2
        if(papdu.p1 != (byte)0x00 || (papdu.p2 >= (byte)0x16 && papdu.p2 <= (byte)0x18))  
            ISOException.throwIt(ISO7816.SW_WRONG_P1P2);
        
        //��Կ�����Ƿ���ȷ
        if(papdu.lc != (byte)0x15)
            ISOException.throwIt(ISO7816.SW_WRONG_LENGTH);  
  
      //�ļ��ռ�����  
        if(keyfile.recNum >= 3)
            ISOException.throwIt(ISO7816.SW_FILE_FULL);  
  
        keyfile.addkey(papdu.p2, papdu.lc, papdu.pdata);//д��һ����Կ  
  
        return true;  
    }  

    /*
     * ��дѡ����
     * ��01 Java���ܿ�֮������ P30
     */
    private boolean write_read_bin(byte U_R){
    	switch(U_R){
    		case (byte)'U': return write_binary();
    		case (byte)'R': return read_binary();
    		default : return false;
    	}
    }
    
    /**
     * д�������ļ�
     * д�������ļ�ֻ��Ҫһ������
     * ������������ݻ����Ҫ����
     * ��д���Ѵ����ĳֿ��˻����ļ���Ӧ�û����ļ��У�
     * ������ע�⣺д��ǰҪ�ȼ��д��������Ƿ񳬹��ļ��޶��Ĵ�С
     * @return
     */
    private boolean write_binary() {  
    	
    	//�����ж�cla�Ƿ�ȷʵΪ0x00�����ܼ���ִ��
        if(papdu.cla != (byte)0x00)  
            ISOException.throwIt(ISO7816.SW_CLA_NOT_SUPPORTED);  
    	
        //ÿ��Ӧ��ֻ����һ��KEY�ļ����ұ������Ƚ���
        //��02 ����Ǯ�����ļ�ϵͳ��P19
        if(keyfile == null)
            ISOException.throwIt(ISO7816.SW_FILE_NOT_FOUND);
  
        //д��ǰҪ�ȼ��д��������Ƿ񳬹��ļ��޶��Ĵ�С
        //д��data����Ӧ����1-255֮��
        if(papdu.lc <= 0x00 && papdu.lc >= 0xFF)
            ISOException.throwIt(ISO7816.SW_WRONG_LENGTH);

        /*
         * �ֿ��˻��߿���Ӧ��Ϊ�գ������Ϊ�վͿ��Կ�ʼд�ļ���
         */
        //Ӧ�û����ļ�Ϊ0x16��Ӧ���ļ���Ӧ��Ϊ��
        if(papdu.p1 == (byte)0x16 && cardfile == null)  
            ISOException.throwIt(ISO7816.SW_CONDITIONS_NOT_SATISFIED);
        //�ֿ��˻����ļ�Ϊ0x17���ֿ��˲�Ӧ��Ϊnull
        if(papdu.p1 == (byte)0x17 && personfile == null)  
            ISOException.throwIt(ISO7816.SW_CONDITIONS_NOT_SATISFIED);  

        //Ӧ�û����ļ�Ϊ0x16�� �ֿ��˻����ļ�Ϊ0x17
        if(papdu.p1 == (byte)0x16){
            cardfile.write_bineary(papdu.p2, papdu.lc, papdu.pdata); 
        } else if(papdu.p1 == (byte)0x17){
            personfile.write_bineary(papdu.p2, papdu.lc, papdu.pdata);  
        }
  
        return true;  
    }  
    
    /**
     * ��ȡ�������ļ�
     * ����  
     * @return
     */
    private boolean read_binary() {  
    	
    	//�����ж�cla�Ƿ�ȷʵΪ0x00�����ܼ���ִ��
        if(papdu.cla != (byte)0x00)  
            ISOException.throwIt(ISO7816.SW_CLA_NOT_SUPPORTED);  
    	
        if(keyfile == null)
            ISOException.throwIt(ISO7816.SW_FILE_NOT_FOUND);

        //Ӧ�û����ļ�Ϊ0x16��Ӧ���ļ���Ӧ��Ϊ��
        if(papdu.p1 == (byte)0x16 && cardfile == null)  
            ISOException.throwIt(ISO7816.SW_CONDITIONS_NOT_SATISFIED);
        //�ֿ��˻����ļ�Ϊ0x17���ֿ��˲�Ӧ��Ϊnull
        if(papdu.p1 == (byte)0x17 && personfile == null)  
            ISOException.throwIt(ISO7816.SW_CONDITIONS_NOT_SATISFIED);  
  
        //��ȡ��Ӧ�Ķ����ļ�  
        if(papdu.p1 == (byte)0x16){
            this.cardfile.read_binary(papdu.p2, papdu.le, papdu.pdata);
        }
        else if(papdu.p1 == (byte)0x17){
            this.personfile.read_binary(papdu.p2, papdu.le, papdu.pdata);
        }
  
        return true;  
    }  
	
	/*
	 * ���ܣ������ļ�
	 */
	private boolean create_file() {
		// �ж�DATA���ļ�������Ϣ��AEF��
		switch(papdu.pdata[0]){
		case condef.EP_FILE:        return EP_file();   	// 0x2F����Ǯ���ļ�
		//todo:��ɴ�����Կ�ļ����ֿ��˻����ļ���Ӧ�û����ļ�
        case (short)0x39:    		return Person_file(); 	// 0x39�ֿ��˻����ļ�
        case (short)0x38:		    return APP_file();  	// 0x38Ӧ�û����ļ�
		case (short)0x3F:       	return Key_file();  	// 0x3F��Կ�ļ�
		default: 
			ISOException.throwIt(ISO7816.SW_FUNC_NOT_SUPPORTED);
		}
		return true;
	}
	/*
	 * ���ܣ���������Ǯ���ļ�
	 * �����������ļ���ʵ�ַ�ʽ����
	 */
	private boolean EP_file() {
		// CLA��ʶ����ӿ�
		if(papdu.cla != (byte)0x80)
			ISOException.throwIt(ISO7816.SW_CLA_NOT_SUPPORTED);
		
		// LC��Data Field֮����
		// �ļ�����ʱ�ļ���Ϣ����Ϊ0x07
		if(papdu.lc != (byte)0x07)
			ISOException.throwIt(ISO7816.SW_WRONG_LENGTH);
		
		// ����Ѿ�������
		if(EPfile != null)
			ISOException.throwIt(ISO7816.SW_CONDITIONS_NOT_SATISFIED);
        //ÿ��Ӧ��ֻ����һ��KEY�ļ����ұ������Ƚ���
        //��02 ����Ǯ�����ļ�ϵͳ��P19
        if(keyfile == null)
            ISOException.throwIt(ISO7816.SW_FILE_NOT_FOUND);  
		
		EPfile = new EPFile(keyfile);
		
		return true;
	}	

	/*
	 * 
	 * �ֿ�����Ϣ�ļ�  
	 */
    private boolean Person_file() {  
        if(papdu.cla != (byte)0x80)  
            ISOException.throwIt(ISO7816.SW_CLA_NOT_SUPPORTED);  
  
        if(papdu.lc != (byte)0x07)  
            ISOException.throwIt(ISO7816.SW_WRONG_LENGTH);  
  
        if(personfile != null)
            ISOException.throwIt(ISO7816.SW_CONDITIONS_NOT_SATISFIED);
        
        //ÿ��Ӧ��ֻ����һ��KEY�ļ����ұ������Ƚ���
        //��02 ����Ǯ�����ļ�ϵͳ��P19
        if(keyfile == null)
            ISOException.throwIt(ISO7816.SW_FILE_NOT_FOUND);  
  
        //������д��personfile
        personfile = new BinaryFile(papdu.pdata);
  
        return true;  
    }  
	    
    /*
     * Key_file()
     * ��Կ�ļ�  
     * ����EP_fileʵ��
     */
    private boolean Key_file() {

        if(papdu.cla != (byte)0x80)  
            ISOException.throwIt(ISO7816.SW_CLA_NOT_SUPPORTED);  
  
        // LC Ӧ��Ϊ0x07, word��˵��15�д�
        if(papdu.lc != (byte)0x07)  
            ISOException.throwIt(ISO7816.SW_WRONG_LENGTH);  
  
        if(keyfile != null)
            ISOException.throwIt(ISO7816.SW_FILE_NOT_FOUND);  
  
        //keyfile��Key_file�д������ʲ���Ҫ�ж��Ƿ�Ϊnull
        keyfile = new KeyFile();
  
        return true;  
    }  
    
    /*
     * Ӧ�� 
     * ����Ӧ�û����ļ�  
     */
    private boolean APP_file() {  
    	
        if(papdu.cla != (byte)0x80)  
            ISOException.throwIt(ISO7816.SW_CLA_NOT_SUPPORTED);  
  
        if(papdu.lc != (byte)0x07)  
            ISOException.throwIt(ISO7816.SW_WRONG_LENGTH);  
  
        if(cardfile != null)
            ISOException.throwIt(ISO7816.SW_CONDITIONS_NOT_SATISFIED);  
        
        if(keyfile == null)
            ISOException.throwIt(ISO7816.SW_FILE_NOT_FOUND);  
  
        //������д��cardfile
        cardfile = new BinaryFile(papdu.pdata);
  
        return true;  
    }  
    
    /*
	 * ���ܣ�Ȧ�������ʵ��
	 */
	private boolean load() {
		short rc;
		
		if(papdu.cla != (byte)0x80)
			ISOException.throwIt(ISO7816.SW_CLA_NOT_SUPPORTED);
		
		if(papdu.p1 != (byte)0x00 && papdu.p2 != (byte)0x00)
			ISOException.throwIt(ISO7816.SW_WRONG_P1P2);
		
		if(EPfile == null)
			ISOException.throwIt(ISO7816.SW_FILE_NOT_FOUND);
		
		if(papdu.lc != (short)0x0B)
			ISOException.throwIt(ISO7816.SW_WRONG_LENGTH);
		
		rc = EPfile.load(papdu.pdata);
		
		if(rc == 1)//MAC2��֤δͨ��  
			ISOException.throwIt(ISO7816.SW_SECURITY_STATUS_NOT_SATISFIED);
		else if(rc == 2)
			ISOException.throwIt(condef.SW_LOAD_FULL);
		else if(rc == 3)
			ISOException.throwIt(ISO7816.SW_RECORD_NOT_FOUND);
		
		papdu.le = (short)4;
		
		return true;
	}

	/*
	 * ���ܣ�Ȧ���ʼ�������ʵ��
	 */
	private boolean init_load() {
		short num,rc = 0;
		
		if(papdu.cla != (byte)0x80)
			ISOException.throwIt(ISO7816.SW_CLA_NOT_SUPPORTED);
		
		if(papdu.p1 != (byte)0x00 && papdu.p2 != (byte)0x02)
			ISOException.throwIt(ISO7816.SW_WRONG_P1P2);
		
		if(papdu.lc != (short)0x0B)
			ISOException.throwIt(ISO7816.SW_WRONG_LENGTH);
		
		if(EPfile == null)
			ISOException.throwIt(ISO7816.SW_FILE_NOT_FOUND);
		
		num = keyfile.findkey(papdu.pdata[0]);
		
		
		if(num == 0x00) //��ʾ�Ҳ�����Ӧ��Կ 
			ISOException.throwIt(ISO7816.SW_RECORD_NOT_FOUND);
		
		rc = EPfile.init4load(num, papdu.pdata);//����0��ʾ�ɹ�,����2��ʾ����  
		
		if(rc == 2)
			ISOException.throwIt((condef.SW_LOAD_FULL));		
		
		papdu.le = (short)0x10; // �ƺ������⣿��
		
		return true;
	}
		/*
	 * ���ܣ����������ʵ��
	 */
	private boolean purchase(){
		return true;
	}
	/*
	 * ���ܣ�����ѯ���ܵ�ʵ��
	 */
	private boolean get_balance(){
		return true;
	}
	
	/*
	 * ���ܣ����ѳ�ʼ����ʵ��
	 */
	private boolean init_purchase(){
		return true;
	}
}