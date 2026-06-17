import { ComponentFixture, TestBed } from '@angular/core/testing';

import { FlowTrace } from './flow-trace';

describe('FlowTrace', () => {
  let component: FlowTrace;
  let fixture: ComponentFixture<FlowTrace>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [FlowTrace],
    }).compileComponents();

    fixture = TestBed.createComponent(FlowTrace);
    component = fixture.componentInstance;
    await fixture.whenStable();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
