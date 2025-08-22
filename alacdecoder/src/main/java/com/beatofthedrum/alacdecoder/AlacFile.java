/*
** AlacFile.java
**
** Copyright (c) 2011 Peter McQuillan
**
** All Rights Reserved.
**                       
** Distributed under the BSD Software License (see license.txt)  
**
*/
package com.beatofthedrum.alacdecoder;

public class AlacFile
{

	byte input_buffer[];
	int ibIdx = 0;
	int input_buffer_bitaccumulator = 0; /* used so we can do arbitary
						bit reads */

	int numchannels = 0;
	public int bytespersample = 0;

    LeadingZeros lz = new LeadingZeros();


    private final int buffer_size = 16384;
    /* buffers */
	int predicterror_buffer_a[] = new int[buffer_size];
	int predicterror_buffer_b[] = new int[buffer_size];

	int outputsamples_buffer_a[] = new int[buffer_size];
	int outputsamples_buffer_b[] = new int[buffer_size];

	int uncompressed_bytes_buffer_a[] = new int[buffer_size];
	int uncompressed_bytes_buffer_b[] = new int[buffer_size];



	/* stuff from setinfo */
	public int setinfo_max_samples_per_frame = 0; // 0x1000 = 4096
	int current_version = 0; // 0x00
	int setinfo_sample_size = 0; // 0x10
	int setinfo_rice_historymult = 0; // 0x28
	int setinfo_rice_initialhistory = 0; // 0x0a
	int setinfo_rice_kmodifier = 0; // 0x0e
	int max_run = 0; // 0x00ff
	int max_frame_bytes = 0; // 0x000020e7
	int average_bit_rate = 0; // 0x00069fe4
	int sample_rate = 0; // 0x0000ac44
	/* end setinfo stuff */

    int[] predictor_coef_table = new int[1024];
    int[] predictor_coef_table_a = new int[1024];
    int[] predictor_coef_table_b = new int[1024];
}